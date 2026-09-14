package com.mosaicglobal.finance.shared.security;

import com.mosaicglobal.finance.shared.kernel.UserId;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Reads identity information from the validated JWT. Adapters use this; domain never does. */
public final class AuthenticatedUser {

  /** Private claim carrying the device the token was issued for. */
  public static final String DEVICE_CLAIM = "did";

  private AuthenticatedUser() {}

  public static Optional<UserId> current() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth instanceof JwtAuthenticationToken token
        ? Optional.of(userId(token.getToken()))
        : Optional.empty();
  }

  public static UserId requireCurrent() {
    return current()
        .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("No authenticated user"));
  }

  public static UserId userId(Jwt jwt) {
    return UserId.of(jwt.getSubject());
  }

  public static Optional<String> deviceId(Jwt jwt) {
    return Optional.ofNullable(jwt.getClaimAsString(DEVICE_CLAIM));
  }
}
