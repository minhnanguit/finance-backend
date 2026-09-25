package com.mosaicglobal.finance.shared.security;

import java.util.Objects;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The identity claims this system trusts from the external IdP, extracted from a validated token.
 *
 * <p>Deliberately narrow: {@link CurrentUserResolver} implementations receive only what they need
 * to find or provision a local user, never the whole {@link Jwt}.
 *
 * @param subject stable, opaque id of the account at the IdP (the {@code sub} claim) — never the
 *     e-mail, which the user can change
 */
public record SubjectClaims(String subject, String email, String displayName) {

  public SubjectClaims {
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(email, "email");
    Objects.requireNonNull(displayName, "displayName");
  }

  /** Reads the standard OIDC claims, falling back so a missing optional claim never 500s. */
  public static SubjectClaims from(Jwt jwt) {
    String email = claimOrNull(jwt, "email");
    String name = claimOrNull(jwt, "name");
    String preferredUsername = claimOrNull(jwt, "preferred_username");
    String resolvedEmail = email != null ? email : preferredUsername;
    return new SubjectClaims(
        jwt.getSubject(),
        resolvedEmail != null ? resolvedEmail : jwt.getSubject(),
        firstNonBlank(name, preferredUsername, resolvedEmail, jwt.getSubject()));
  }

  private static String claimOrNull(Jwt jwt, String name) {
    String value = jwt.getClaimAsString(name);
    return value == null || value.isBlank() ? null : value;
  }

  private static String firstNonBlank(String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("no usable display name claim");
  }
}
