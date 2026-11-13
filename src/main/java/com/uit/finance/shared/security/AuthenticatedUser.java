package com.uit.finance.shared.security;

import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Internal id của caller — chỉ adapter dùng. Luôn là id trong {@code users}, không bao giờ là
 * {@code sub}: business table reference id này để đổi IdP không phải migrate (ADR-004).
 */
public final class AuthenticatedUser {

  static final String AUTH_TIME_CLAIM = "auth_time";

  private AuthenticatedUser() {}

  public static Optional<UserId> current() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth instanceof AuthenticatedUserToken token
        ? Optional.of(token.userId())
        : Optional.empty();
  }

  public static UserId requireCurrent() {
    return current()
        .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("No authenticated user"));
  }

  /**
   * Lúc user nhập mật khẩu/passkey lần gần nhất (claim {@code auth_time} của OIDC), không phải lúc
   * token được phát: refresh token không làm mốc này mới lên. Dùng cho thao tác đòi đăng nhập lại
   * (xoá tài khoản, ADR-008). Rỗng nếu token không có claim.
   */
  public static Optional<Instant> authenticationTime() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken token) {
      return Optional.ofNullable(token.getToken().getClaimAsInstant(AUTH_TIME_CLAIM));
    }
    return Optional.empty();
  }
}
