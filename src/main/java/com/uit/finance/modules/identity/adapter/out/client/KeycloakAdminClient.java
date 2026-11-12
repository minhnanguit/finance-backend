package com.uit.finance.modules.identity.adapter.out.client;

import com.uit.finance.modules.identity.adapter.config.IdentityProperties;
import com.uit.finance.modules.identity.adapter.config.IdentityProperties.KeycloakAdmin;
import com.uit.finance.modules.identity.application.port.out.IdentityProviderAccountsPort;
import com.uit.finance.modules.identity.domain.model.ExternalSubject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Keycloak Admin REST API qua client {@code finance-backend} (client credentials, service account
 * chỉ có quyền {@code realm-management/manage-users}, ADR-008 A8). Dùng {@link RestClient} trơn
 * thay vì {@code keycloak-admin-client} để không bị buộc version thư viện theo version Keycloak.
 *
 * <p>Access token của service account được giữ tới gần hết hạn; Keycloak trả 401 (token bị thu hồi,
 * đổi key) thì lấy token mới và thử lại đúng một lần. Lỗi khác ném exception; job xoá thử lại ở lần
 * chạy sau (việc còn nợ nằm trong {@code users.idp_removed_at}, không mất).
 */
@Component
class KeycloakAdminClient implements IdentityProviderAccountsPort {

  private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

  /** Lấy token mới sớm hơn hạn một chút để không gửi token vừa hết hạn trên đường đi. */
  private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

  private final KeycloakAdmin config;
  private final RestClient http;
  private final Clock clock;
  private volatile @Nullable CachedToken token;

  KeycloakAdminClient(IdentityProperties properties, Clock clock) {
    this.config = properties.keycloakAdmin();
    this.clock = clock;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(config.connectTimeout());
    requestFactory.setReadTimeout(config.readTimeout());
    this.http =
        RestClient.builder()
            .baseUrl(config.baseUrl())
            .requestFactory(requestFactory)
            // Tự đọc status bên dưới: 404 khi xoá là "đã xoá rồi", không phải lỗi.
            .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
            .build();
    if (config.enabled() && isBlank(config.clientSecret())) {
      // Không chặn khởi động (Keycloak không phải phụ thuộc cứng của mọi request), nhưng phải thấy
      // ngay:
      // thiếu secret thì tài khoản Keycloak của user đã xoá sẽ nằm chờ cho tới khi được cấu hình.
      log.error(
          "app.identity.keycloak-admin.client-secret is not set (KEYCLOAK_ADMIN_CLIENT_SECRET): "
              + "deleted accounts stay in Keycloak until it is configured");
    }
  }

  @Override
  public boolean enabled() {
    return config.enabled();
  }

  @Override
  public void deleteAccount(ExternalSubject subject) {
    if (!config.enabled()) {
      throw new IllegalStateException("Keycloak admin client is disabled");
    }
    ResponseEntity<Void> response = deleteUser(subject, accessToken(false));
    if (response.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
      response = deleteUser(subject, accessToken(true));
    }
    HttpStatusCode status = response.getStatusCode();
    if (status.is2xxSuccessful() || status.value() == HttpStatus.NOT_FOUND.value()) {
      return;
    }
    throw new IdentityProviderUnavailableException("delete user", status.value());
  }

  private ResponseEntity<Void> deleteUser(ExternalSubject subject, String accessToken) {
    // Với Keycloak, claim `sub` chính là id của user trong realm.
    return http.delete()
        .uri("/admin/realms/{realm}/users/{id}", config.realm(), subject.value())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .retrieve()
        .toBodilessEntity();
  }

  private String accessToken(boolean forceRefresh) {
    CachedToken cached = token;
    Instant now = clock.instant();
    if (!forceRefresh && cached != null && now.isBefore(cached.expiresAt())) {
      return cached.value();
    }
    CachedToken fresh = requestToken(now);
    token = fresh;
    return fresh.value();
  }

  private CachedToken requestToken(Instant now) {
    if (isBlank(config.clientSecret())) {
      throw new IllegalStateException("app.identity.keycloak-admin.client-secret is not set");
    }
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", config.clientId());
    form.add("client_secret", config.clientSecret());
    ResponseEntity<Map<String, Object>> response =
        http.post()
            .uri("/realms/{realm}/protocol/openid-connect/token", config.realm())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .toEntity(new ParameterizedTypeReference<>() {});
    Map<String, Object> body = response.getBody();
    if (!response.getStatusCode().is2xxSuccessful()
        || body == null
        || !(body.get("access_token") instanceof String accessToken)) {
      throw new IdentityProviderUnavailableException(
          "client credentials token", response.getStatusCode().value());
    }
    long expiresIn = body.get("expires_in") instanceof Number seconds ? seconds.longValue() : 60;
    return new CachedToken(accessToken, now.plusSeconds(expiresIn).minus(EXPIRY_MARGIN));
  }

  private static boolean isBlank(@Nullable String value) {
    return value == null || value.isBlank();
  }

  private record CachedToken(String value, Instant expiresAt) {}
}
