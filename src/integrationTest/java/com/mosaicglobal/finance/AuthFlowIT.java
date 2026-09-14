package com.mosaicglobal.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/** End-to-end through HTTP, security filter chain, idempotency filter, JPA and Flyway. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthFlowIT {

  @LocalServerPort int port;

  private ApiClient api;

  @BeforeEach
  void setUp() {
    api = new ApiClient(port);
  }

  private static String uniqueEmail() {
    return "it-" + UUID.randomUUID() + "@example.com";
  }

  @Test
  void registerThenReadOwnProfile() {
    String email = uniqueEmail();

    ResponseEntity<String> registered =
        api.post("/api/v1/auth/register", ApiClient.registerBody(email));
    assertThat(registered.getStatusCode().value()).isEqualTo(201);
    JsonNode tokens = api.json(registered);
    assertThat(tokens.get("tokenType").asString()).isEqualTo("Bearer");
    assertThat(tokens.get("expiresIn").asInt()).isEqualTo(900);
    assertThat(tokens.get("accessToken").asString()).isNotBlank();
    assertThat(tokens.get("refreshToken").asString()).isNotBlank();

    ResponseEntity<String> me = api.get("/api/v1/me", tokens.get("accessToken").asString());
    assertThat(me.getStatusCode().value()).isEqualTo(200);
    JsonNode profile = api.json(me);
    assertThat(profile.get("email").asString()).isEqualTo(email);
    assertThat(profile.get("displayName").asString()).isEqualTo("Alice");
    assertThat(profile.has("id")).isTrue();
    assertThat(profile.has("createdAt")).isTrue();
  }

  @Test
  void protectedEndpointWithoutTokenIsAProblemDetail() {
    ResponseEntity<String> me = api.get("/api/v1/me", null);
    assertThat(me.getStatusCode().value()).isEqualTo(401);
    assertThat(me.getHeaders().getContentType().toString()).startsWith("application/problem+json");
    assertThat(api.json(me).get("code").asString()).isEqualTo("auth.unauthenticated");
  }

  @Test
  void duplicateEmailIsConflictWithStableCode() {
    String email = uniqueEmail();
    api.post("/api/v1/auth/register", ApiClient.registerBody(email));

    ResponseEntity<String> again = api.post("/api/v1/auth/register", ApiClient.registerBody(email));

    assertThat(again.getStatusCode().value()).isEqualTo(409);
    assertThat(api.json(again).get("code").asString()).isEqualTo("identity.email_taken");
  }

  @Test
  void loginWithWrongPasswordIsUnauthorizedAndVague() {
    String email = uniqueEmail();
    api.post("/api/v1/auth/register", ApiClient.registerBody(email));

    ResponseEntity<String> wrong =
        api.post("/api/v1/auth/login", ApiClient.loginBody(email, "nope nope nope"));
    ResponseEntity<String> unknown =
        api.post("/api/v1/auth/login", ApiClient.loginBody(uniqueEmail(), "nope nope nope"));

    assertThat(wrong.getStatusCode().value()).isEqualTo(401);
    assertThat(unknown.getStatusCode().value()).isEqualTo(401);
    assertThat(api.json(wrong).get("code").asString()).isEqualTo("identity.invalid_credentials");
    assertThat(api.json(unknown).get("code").asString()).isEqualTo("identity.invalid_credentials");
  }

  @Test
  void refreshRotatesAndReuseRevokesTheWholeDevice() {
    String email = uniqueEmail();
    JsonNode first = api.json(api.post("/api/v1/auth/register", ApiClient.registerBody(email)));
    String firstRefresh = first.get("refreshToken").asString();

    ResponseEntity<String> rotated =
        api.post("/api/v1/auth/refresh", ApiClient.refreshBody(firstRefresh));
    assertThat(rotated.getStatusCode().value()).isEqualTo(200);
    String secondRefresh = api.json(rotated).get("refreshToken").asString();
    assertThat(secondRefresh).isNotEqualTo(firstRefresh);

    ResponseEntity<String> reuse =
        api.post("/api/v1/auth/refresh", ApiClient.refreshBody(firstRefresh));
    assertThat(reuse.getStatusCode().value()).isEqualTo(401);
    assertThat(api.json(reuse).get("code").asString()).isEqualTo("identity.refresh_token_reused");

    ResponseEntity<String> collateral =
        api.post("/api/v1/auth/refresh", ApiClient.refreshBody(secondRefresh));
    assertThat(collateral.getStatusCode().value())
        .as("successor revoked after reuse")
        .isEqualTo(401);
  }

  @Test
  void logoutRevokesTheSession() {
    String email = uniqueEmail();
    JsonNode tokens = api.json(api.post("/api/v1/auth/register", ApiClient.registerBody(email)));
    String access = tokens.get("accessToken").asString();
    String refresh = tokens.get("refreshToken").asString();

    ResponseEntity<String> logout =
        api.post(
            "/api/v1/auth/logout",
            Map.of("refreshToken", refresh),
            UUID.randomUUID().toString(),
            access);
    assertThat(logout.getStatusCode().value()).isEqualTo(204);

    ResponseEntity<String> afterLogout =
        api.post("/api/v1/auth/refresh", ApiClient.refreshBody(refresh));
    assertThat(afterLogout.getStatusCode().value()).isEqualTo(401);
  }

  @Test
  void idempotencyKeyReplaysAndProtectsAgainstReuse() {
    String email = uniqueEmail();
    String key = UUID.randomUUID().toString();
    Map<String, Object> body = ApiClient.registerBody(email);

    ResponseEntity<String> first = api.post("/api/v1/auth/register", body, key, null);
    ResponseEntity<String> replay = api.post("/api/v1/auth/register", body, key, null);
    ResponseEntity<String> tampered =
        api.post("/api/v1/auth/register", ApiClient.registerBody(uniqueEmail()), key, null);
    ResponseEntity<String> missing =
        api.post("/api/v1/auth/register", ApiClient.registerBody(uniqueEmail()), null, null);

    assertThat(first.getStatusCode().value()).isEqualTo(201);
    assertThat(replay.getStatusCode().value()).isEqualTo(201);
    assertThat(replay.getBody()).isEqualTo(first.getBody());
    assertThat(replay.getHeaders().getFirst("Idempotency-Replayed")).isEqualTo("true");
    assertThat(tampered.getStatusCode().value()).isEqualTo(422);
    assertThat(api.json(tampered).get("code").asString())
        .isEqualTo("request.idempotency_key_reused");
    assertThat(missing.getStatusCode().value()).isEqualTo(400);
    assertThat(api.json(missing).get("code").asString())
        .isEqualTo("request.idempotency_key_invalid");
  }

  @Test
  void validationFailuresListFields() {
    Map<String, Object> bad =
        Map.of(
            "email",
            "not-an-email",
            "password",
            "short",
            "displayName",
            "",
            "device",
            ApiClient.DEVICE);

    ResponseEntity<String> response = api.post("/api/v1/auth/register", bad);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    JsonNode problem = api.json(response);
    assertThat(problem.get("code").asString()).isEqualTo("request.validation_failed");
    assertThat(problem.get("errors").isArray()).isTrue();
    assertThat(problem.get("errors").size()).isGreaterThanOrEqualTo(2);
  }
}
