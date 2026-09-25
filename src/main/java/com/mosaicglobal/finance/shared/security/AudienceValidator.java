package com.mosaicglobal.finance.shared.security;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects a token that was not minted for this API.
 *
 * <p>
 * Without this check any token from the same realm — including one issued to a
 * different client
 * — would be accepted. Keycloak only adds {@code finance-api} to {@code aud}
 * because of an explicit
 * audience mapper in the realm; see {@code deploy/keycloak/README.md}.
 */
final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

  private final String requiredAudience;

  AudienceValidator(String requiredAudience) {
    this.requiredAudience = requiredAudience;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    List<String> audience = token.getAudience();
    if (audience != null && audience.contains(requiredAudience)) {
      return OAuth2TokenValidatorResult.success();
    }
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN,
            "Token is not intended for audience " + requiredAudience,
            null));
  }
}
