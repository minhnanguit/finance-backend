package com.mosaicglobal.finance.modules.identity.domain.model;

import com.mosaicglobal.finance.shared.kernel.Ensure;
import java.util.Locale;
import java.util.regex.Pattern;

/** Dùng {@link #of} để normalize (lowercase, trim) trước khi so sánh. */
public record Email(String value) {

  private static final int MAX_LENGTH = 254;
  private static final Pattern SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  public Email {
    Ensure.notBlank(value, "email");
    Ensure.maxLength(value, MAX_LENGTH, "email");
    if (!SHAPE.matcher(value).matches()) {
      throw new IllegalArgumentException("email is malformed");
    }
  }

  public static Email of(String raw) {
    return new Email(Ensure.notBlank(raw, "email").trim().toLowerCase(Locale.ROOT));
  }

  @Override
  public String toString() {
    return value;
  }
}
