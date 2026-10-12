package com.uit.finance;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Fake Keycloak: RSA key, JWKS endpoint thật qua HTTP, và token được sign bằng key đó.
 *
 * <p>Cố ý không mock {@code JwtDecoder}: app tự fetch JWKS qua network và tự verify signature /
 * issuer / audience, nên đó chính là code được test. Chỉ thiếu login UI của Keycloak — phần đó test
 * trên emulator. Dùng HTTP server có sẵn của JDK để không thêm dependency.
 */
public final class TestIdentityProvider {

  public static final String ISSUER = "https://test-idp.local/realms/finance";
  public static final String AUDIENCE = "finance-api";

  private static final TestIdentityProvider INSTANCE = new TestIdentityProvider();

  private final RSAKey key;
  private final HttpServer server;

  private TestIdentityProvider() {
    try {
      this.key = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
      this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      byte[] jwks = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
      server.createContext(
          "/jwks",
          exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            exchange.getResponseBody().write(jwks);
            exchange.close();
          });
      server.start();
    } catch (Exception e) {
      throw new IllegalStateException("could not start the test identity provider", e);
    }
  }

  public static TestIdentityProvider instance() {
    return INSTANCE;
  }

  public String jwkSetUri() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
  }

  /** Token mà app phải accept. */
  public String validToken(String subject, String email, String displayName) {
    return token(ISSUER, AUDIENCE, subject, email, displayName, Instant.now().plusSeconds(300));
  }

  String token(
      String issuer,
      String audience,
      String subject,
      String email,
      String displayName,
      Instant expiresAt) {
    try {
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .issuer(issuer)
              .audience(List.of(audience))
              .subject(subject)
              .issueTime(Date.from(Instant.now().minusSeconds(5)))
              .expirationTime(Date.from(expiresAt))
              .jwtID(UUID.randomUUID().toString())
              .claim("email", email)
              .claim("preferred_username", email)
              .claim("name", displayName)
              .build();
      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256)
                  .keyID(key.getKeyID())
                  .type(JOSEObjectType.JWT)
                  .build(),
              claims);
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    } catch (Exception e) {
      throw new IllegalStateException("could not mint a test token", e);
    }
  }
}
