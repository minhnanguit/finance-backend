package com.mosaicglobal.finance.shared.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Turns a validated token into an {@link AuthenticatedUserToken}, resolving (and, on first sight,
 * provisioning) the local user in the process.
 *
 * <p>This is the single place where an external subject becomes an internal {@link
 * com.mosaicglobal.finance.shared.kernel.UserId}. Doing it here rather than in a separate filter
 * means there is no ordering to get wrong and no request that can reach a controller unresolved.
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
