package com.uit.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Authentication flow end-to-end: token có signature thật, được verify signature / issuer /
 * audience qua JWKS endpoint thật, và local account được provision ở lần đầu.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class JitProvisioningIT {

  @LocalServerPort int port;
  @Autowired JdbcClient jdbc;

  @DynamicPropertySource
  static void identityProvider(DynamicPropertyRegistry registry) {
    TestIdentityProvider idp = TestIdentityProvider.instance();
    registry.add("app.security.issuer-uri", () -> TestIdentityProvider.ISSUER);
    registry.add("app.security.jwk-set-uri", idp::jwkSetUri);
    registry.add("app.security.audience", () -> TestIdentityProvider.AUDIENCE);
    // Tắt cache để mọi assert bên dưới thấy DB thật, không phải kết quả cached.
    registry.add("app.identity.subject-cache-ttl", () -> "0s");
  }

  private int usersWithSubject(String subject) {
    return jdbc.sql("select count(*) from users where external_subject = :s")
        .param("s", subject)
        .query(Integer.class)
        .single();
  }

  @Test
  @DisplayName("request có token đầu tiên tạo đúng một local account")
  void firstRequestProvisions() {
    ApiClient api = new ApiClient(port);
    String subject = "sub-" + UUID.randomUUID();
    String email = subject + "@example.com";
    String token = TestIdentityProvider.instance().validToken(subject, email, "Ann Example");

    var response = api.get("/api/v1/me", token);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    var body = api.json(response);
    assertThat(body.get("email").asString()).isEqualTo(email);
    assertThat(body.get("displayName").asString()).isEqualTo("Ann Example");
    assertThat(usersWithSubject(subject)).isEqualTo(1);

    // Id trả cho client là internal id, không phải sub.
    assertThat(body.get("id").asString()).isNotEqualTo(subject);
  }

  @Test
  @DisplayName("request lặp lại thì reuse account, không tạo thêm")
  void repeatRequestsAreIdempotent() {
    ApiClient api = new ApiClient(port);
    String subject = "sub-" + UUID.randomUUID();
    String token =
        TestIdentityProvider.instance().validToken(subject, subject + "@example.com", "Ann");

    String firstId = api.json(api.get("/api/v1/me", token)).get("id").asString();
    String secondId = api.json(api.get("/api/v1/me", token)).get("id").asString();

    assertThat(secondId).isEqualTo(firstId);
    assertThat(usersWithSubject(subject)).isEqualTo(1);
  }

  @Test
  @DisplayName("profile đổi trên Keycloak được update ở request kế tiếp")
  void profileIsSynced() {
    ApiClient api = new ApiClient(port);
    String subject = "sub-" + UUID.randomUUID();
    TestIdentityProvider idp = TestIdentityProvider.instance();

    String firstId =
        api.json(api.get("/api/v1/me", idp.validToken(subject, "old@example.com", "Old Name")))
            .get("id")
            .asString();
    var updated =
        api.json(api.get("/api/v1/me", idp.validToken(subject, "new@example.com", "New Name")));

    assertThat(updated.get("id").asString()).isEqualTo(firstId);
    assertThat(updated.get("email").asString()).isEqualTo("new@example.com");
    assertThat(updated.get("displayName").asString()).isEqualTo("New Name");
    assertThat(usersWithSubject(subject)).isEqualTo(1);
  }

  @Test
  @DisplayName("token cấp cho audience khác bị reject")
  void wrongAudienceIsRejected() {
    ApiClient api = new ApiClient(port);
    String subject = "sub-" + UUID.randomUUID();
    String token =
        TestIdentityProvider.instance()
            .token(
                TestIdentityProvider.ISSUER,
                "some-other-client",
                subject,
                subject + "@example.com",
                "Ann",
                java.time.Instant.now().plusSeconds(300));

    assertThat(api.get("/api/v1/me", token).getStatusCode().value()).isEqualTo(401);
    assertThat(usersWithSubject(subject)).isZero();
  }

  @Test
  @DisplayName("token từ issuer khác bị reject")
  void wrongIssuerIsRejected() {
    ApiClient api = new ApiClient(port);
    String subject = "sub-" + UUID.randomUUID();
    String token =
        TestIdentityProvider.instance()
            .token(
                "https://evil.example.com/realms/finance",
                TestIdentityProvider.AUDIENCE,
                subject,
                subject + "@example.com",
                "Ann",
                java.time.Instant.now().plusSeconds(300));

    assertThat(api.get("/api/v1/me", token).getStatusCode().value()).isEqualTo(401);
  }

  @Test
  @DisplayName("token expired bị reject")
  void expiredTokenIsRejected() {
    ApiClient api = new ApiClient(port);
    String subject = "sub-" + UUID.randomUUID();
    String token =
        TestIdentityProvider.instance()
            .token(
                TestIdentityProvider.ISSUER,
                TestIdentityProvider.AUDIENCE,
                subject,
                subject + "@example.com",
                "Ann",
                java.time.Instant.now().minusSeconds(60));

    assertThat(api.get("/api/v1/me", token).getStatusCode().value()).isEqualTo(401);
  }

  @Test
  @DisplayName("không có token thì trả 401 problem+json, không redirect")
  void anonymousIsRejected() {
    ApiClient api = new ApiClient(port);

    var response = api.get("/api/v1/me", null);

    assertThat(response.getStatusCode().value()).isEqualTo(401);
    assertThat(api.json(response).get("code").asString()).isEqualTo("auth.unauthenticated");
  }

  @Test
  @DisplayName("các endpoint /auth/* của bản self-issued token đã bị gỡ")
  void legacyAuthEndpointsAreGone() {
    ApiClient api = new ApiClient(port);

    assertThat(api.post("/api/v1/auth/login", java.util.Map.of()).getStatusCode().value())
        .isIn(401, 404);
  }
}
