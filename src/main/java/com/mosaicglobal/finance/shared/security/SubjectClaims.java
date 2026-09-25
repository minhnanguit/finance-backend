package com.mosaicglobal.finance.shared.security;

import java.util.Objects;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Các claim lấy từ token đã verify. Cố ý hẹp: resolver chỉ nhận đúng thứ nó cần, không nhận cả
 * {@link Jwt}.
 *
 * @param subject claim {@code sub} — identifier duy nhất. Không dùng email vì user đổi được.
 */
public record SubjectClaims(String subject, String email, String displayName) {

  public SubjectClaims {
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(email, "email");
    Objects.requireNonNull(displayName, "displayName");
  }

  /** Thiếu optional claim thì fallback sang claim khác, để không bao giờ thành lỗi 500. */
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
