package com.mosaicglobal.finance;

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
 * The authentication path end to end: a signed token arrives, its signature, issuer and audience
 * are checked against a real JWKS endpoint, and the local account is created on first sight.
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
    // No caching, so every assertion below observes the database rather than a memoised answer.
    registry.add("app.identity.subject-cache-ttl", () -> "0s");
  }

  private int usersWithSubject(String subject) {
    return jdbc.sql("select count(*) from users where external_subject = :s")
        .param("s", subject)
        .query(Integer.class)
        .single();
  }

  @Test
  @DisplayName("first authenticated request creates exactly one local account")
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

    // The id handed to clients is ours, not the IdP's subject.
    assertThat(body.get("id").asString()).isNotEqualTo(subject);
  }

  @Test
  @DisplayName("repeat requests reuse the same account instead of creating another")
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
  @DisplayName("a profile changed at the provider is written through on the next request")
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
  @DisplayName("a token minted for another audience is rejected")
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
  @DisplayName("a token from another issuer is rejected")
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
  @DisplayName("an expired token is rejected")
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
  @DisplayName("no token at all is a problem+json 401, not a redirect")
  void anonymousIsRejected() {
    ApiClient api = new ApiClient(port);

    var response = api.get("/api/v1/me", null);

    assertThat(response.getStatusCode().value()).isEqualTo(401);
    assertThat(api.json(response).get("code").asString()).isEqualTo("auth.unauthenticated");
  }

  @Test
  @DisplayName("the auth endpoints of the old self-issued flow are gone")
  void legacyAuthEndpointsAreGone() {
    ApiClient api = new ApiClient(port);

    assertThat(api.post("/api/v1/auth/login", java.util.Map.of()).getStatusCode().value())
        .isIn(401, 404);
  }
}
