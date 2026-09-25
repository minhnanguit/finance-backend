package com.mosaicglobal.finance.shared.security;

import com.mosaicglobal.finance.shared.kernel.UserId;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Internal id của caller — chỉ adapter dùng. Luôn là id trong {@code users}, không bao giờ là
 * {@code sub}: business table reference id này để đổi IdP không phải migrate (ADR-004).
 */
public final class AuthenticatedUser {

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
}
