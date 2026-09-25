package com.mosaicglobal.finance.shared.security;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reject token không được cấp cho API này. Thiếu bước này thì token của bất kỳ client nào cùng
 * realm cũng lọt qua. Keycloak chỉ đưa {@code finance-api} vào {@code aud} nhờ audience mapper
 * trong realm — xem {@code deploy/keycloak/README.md}.
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
