package com.uit.finance.modules.identity.domain.model;

import com.uit.finance.shared.kernel.Ensure;
import com.uit.finance.shared.kernel.UserId;
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

  /**
   * Địa chỉ thay thế cho tài khoản đã xoá (ADR-008 A4): không còn là của ai ({@code .invalid}, RFC
   * 2606), riêng cho từng user nên không vướng unique index, và để email cũ đăng ký lại được ngay.
   */
  public static Email erasedFor(UserId id) {
    return new Email("deleted-" + id.value() + "@erased.invalid");
  }

  @Override
  public String toString() {
    return value;
  }
}
