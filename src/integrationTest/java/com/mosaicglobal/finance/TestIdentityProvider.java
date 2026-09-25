package com.mosaicglobal.finance;

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
 * A stand-in for Keycloak: an RSA key, a real JWKS endpoint over HTTP, and tokens signed with it.
 *
 * <p>Deliberately not a mocked {@code JwtDecoder}. The application fetches the key set over the
 * network and runs its own signature, issuer and audience checks, so those code paths are the ones
 * under test. Only Keycloak's login UI and token endpoint are out of scope — those belong to the
 * end-to-end run on the emulator (Phase 5).
 *
 * <p>Uses the JDK's own HTTP server so no test dependency is added.
 */
final class TestIdentityProvider {

  static final String ISSUER = "https://test-idp.local/realms/finance";
  static final String AUDIENCE = "finance-api";

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

  static TestIdentityProvider instance() {
    return INSTANCE;
  }

  String jwkSetUri() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
  }

  /** A token this application should accept. */
  String validToken(String subject, String email, String displayName) {
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
