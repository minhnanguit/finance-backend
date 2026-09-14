package com.mosaicglobal.finance.shared.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * The backend issues and validates its own RS256 access tokens (see ADR-004). Both sides share one
 * {@link RSAKey} so a token minted here is always verifiable here.
 */
@Configuration(proxyBeanMethods = false)
class JwtKeyConfiguration {

  private static final Logger log = LoggerFactory.getLogger(JwtKeyConfiguration.class);

  @Bean
  RSAKey jwtSigningKey(JwtProperties properties) {
    RSAPublicKey publicKey;
    RSAPrivateKey privateKey;
    if (properties.hasConfiguredKeys()) {
      publicKey = PemKeys.publicKey(properties.publicKeyPem());
      privateKey = PemKeys.privateKey(properties.privateKeyPem());
    } else {
      log.warn(
          "No JWT key pair configured (app.security.jwt.*-key-pem). Generating an EPHEMERAL pair: "
              + "every access token becomes invalid on restart. Do not run like this in production.");
      KeyPair pair = generate();
      publicKey = (RSAPublicKey) pair.getPublic();
      privateKey = (RSAPrivateKey) pair.getPrivate();
    }
    return new RSAKey.Builder(publicKey)
        .privateKey(privateKey)
        .keyID(UUID.randomUUID().toString())
        .build();
  }

  @Bean
  JwtEncoder jwtEncoder(RSAKey key) {
    return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
  }

  @Bean
  JwtDecoder jwtDecoder(RSAKey key, JwtProperties properties) throws JOSEException {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
    return decoder;
  }

  private static KeyPair generate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("RSA not available", e);
    }
  }
}
