package com.mosaicglobal.finance.shared.security;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Access-token settings. Keys are PEM strings (PKCS#8 private, X.509 public). When absent an
 * ephemeral pair is generated at startup – acceptable for local development only.
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
    @DefaultValue("finance-backend") String issuer,
    @DefaultValue("15m") Duration accessTokenTtl,
    @Nullable String privateKeyPem,
    @Nullable String publicKeyPem) {

  public boolean hasConfiguredKeys() {
    return notBlank(privateKeyPem) && notBlank(publicKeyPem);
  }

  private static boolean notBlank(@Nullable String value) {
    return value != null && !value.isBlank();
  }
}
