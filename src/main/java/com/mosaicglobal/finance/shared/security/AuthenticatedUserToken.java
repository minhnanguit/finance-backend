package com.mosaicglobal.finance.shared.security;

import com.mosaicglobal.finance.shared.kernel.UserId;
import java.util.Collection;
import java.util.Objects;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Extend {@link JwtAuthenticationToken} để code viết cho type chuẩn vẫn chạy, đồng thời mang sẵn
 * internal {@link UserId} — không phải đọc lại claim hay query DB.
 */
public final class AuthenticatedUserToken extends JwtAuthenticationToken {

  private final transient UserId userId;

  AuthenticatedUserToken(
      Jwt jwt, UserId userId, Collection<? extends GrantedAuthority> authorities) {
    super(jwt, authorities, jwt.getSubject());
    this.userId = Objects.requireNonNull(userId, "userId");
  }

  /** Id trong table {@code users}, không phải {@code sub}. */
  public UserId userId() {
    return userId;
  }
}
