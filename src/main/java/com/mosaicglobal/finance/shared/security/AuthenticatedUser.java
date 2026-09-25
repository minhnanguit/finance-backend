package com.mosaicglobal.finance.shared.security;

import com.mosaicglobal.finance.shared.kernel.UserId;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The internal id of the caller. Adapters use this; domain and application never do.
 *
 * <p>Always the id of the row in {@code users}, never the IdP's {@code sub} — business tables key
 * off the former so the IdP can be replaced without migrating them (ADR-004).
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
