package com.uit.finance.modules.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.ApiClient;
import com.uit.finance.TestIdentityProvider;
import com.uit.finance.TestcontainersConfiguration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Cache trạng thái với cấu hình mặc định (30 giây): request đầu tiên đã cache "chưa xoá", nên chỉ
 * có listener sau commit mới làm instance này chặn tài khoản ngay sau {@code DELETE /me} (ADR-008
 * A6).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class UserDeletionStatusCacheIT {

  @LocalServerPort int port;

  @DynamicPropertySource
  static void identityProvider(DynamicPropertyRegistry registry) {
    TestIdentityProvider idp = TestIdentityProvider.instance();
    registry.add("app.security.issuer-uri", () -> TestIdentityProvider.ISSUER);
    registry.add("app.security.jwk-set-uri", idp::jwkSetUri);
    registry.add("app.security.audience", () -> TestIdentityProvider.AUDIENCE);
    // Không có Keycloak trong test: consumer xoá tài khoản IdP chỉ ghi log, không gọi mạng.
    registry.add("app.identity.keycloak-admin.enabled", () -> "false");
  }

  @Test
  @DisplayName("instance nhận yêu cầu xoá chặn tài khoản ngay, không đợi hết TTL của cache")
  void deletingInstanceBlocksImmediately() {
    ApiClient api = new ApiClient(port);
    String subject = "sub-" + UUID.randomUUID();
    String token =
        TestIdentityProvider.instance()
            .validToken(subject, subject + "@example.com", "Ann", Instant.now().minusSeconds(5));
    assertThat(api.get("/api/v1/me", token).getStatusCode().value()).isEqualTo(200);

    assertThat(api.delete("/api/v1/me", token).getStatusCode().value()).isEqualTo(202);

    ResponseEntity<String> after = api.get("/api/v1/me", token);
    assertThat(after.getStatusCode().value()).isEqualTo(403);
    assertThat(api.json(after).get("code").asString()).isEqualTo("identity.user_deleted");
  }
}
