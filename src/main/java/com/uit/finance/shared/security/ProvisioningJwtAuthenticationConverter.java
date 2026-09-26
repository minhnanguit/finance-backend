package com.uit.finance.shared.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Nơi duy nhất {@code sub} được map thành internal id (và provision user nếu lần đầu thấy). Làm ở
 * converter thay vì filter riêng: không có filter order nào để làm sai, và không request nào tới
 * được controller khi chưa resolve.
 */
final class ProvisioningJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private final CurrentUserResolver users;
  private final JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();

  ProvisioningJwtAuthenticationConverter(CurrentUserResolver users) {
    this.users = users;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    return new AuthenticatedUserToken(
        jwt, users.resolve(SubjectClaims.from(jwt)), authorities.convert(jwt));
  }
}
