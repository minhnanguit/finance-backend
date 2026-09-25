package com.mosaicglobal.finance.shared.security;

import com.mosaicglobal.finance.shared.kernel.UserId;
import java.util.Collection;
import java.util.Objects;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Authentication carrying both the validated token and the <em>internal</em> user id.
 *
 * <p>Extends {@link JwtAuthenticationToken} so anything written against the standard
 * resource-server type keeps working, while {@link AuthenticatedUser} can hand callers a {@link
 * UserId} without re-reading claims or hitting the database.
 */
public final class AuthenticatedUserToken extends JwtAuthenticationToken {

  private final transient UserId userId;

  AuthenticatedUserToken(
      Jwt jwt, UserId userId, Collection<? extends GrantedAuthority> authorities) {
    super(jwt, authorities, jwt.getSubject());
    this.userId = Objects.requireNonNull(userId, "userId");
  }

  /** The id of the row in {@code users} — not the IdP's {@code sub}. */
  public UserId userId() {
    return userId;
  }
}
