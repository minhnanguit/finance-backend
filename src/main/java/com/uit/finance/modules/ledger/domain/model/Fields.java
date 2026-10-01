package com.uit.finance.modules.ledger.domain.model;

import com.uit.finance.modules.ledger.domain.exception.InvalidFieldException;
import java.util.Currency;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Kiểm tra input đến từ client.
 *
 * <p>Khác {@code Ensure} (ném {@link IllegalArgumentException} cho lỗi lập trình), mọi vi phạm ở
 * đây ném {@link InvalidFieldException} kèm tên field, để sync trả được {@code REJECTED
 * ledger.invalid_field} thay vì lỗi 500 (ADR-005 §3).
 */
final class Fields {

  private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");

  private Fields() {}

  static <T> T required(@Nullable T value, String field) {
    if (value == null) {
      throw new InvalidFieldException(field);
    }
    return value;
  }

  /** Cắt khoảng trắng 2 đầu. Rỗng hoặc dài hơn {@code maxChars} ký tự là lỗi. */
  static String requiredText(@Nullable String raw, int maxChars, String field) {
    return required(optionalText(raw, maxChars, field), field);
  }

  /** Như {@link #requiredText} nhưng rỗng thì trả {@code null}. */
  static @Nullable String optionalText(@Nullable String raw, int maxChars, String field) {
    if (raw == null) {
      return null;
    }
    String text = raw.strip();
    if (text.isEmpty()) {
      return null;
    }
    // Đếm theo code point: một emoji là một ký tự, khớp với char_length() của Postgres.
    if (text.codePointCount(0, text.length()) > maxChars) {
      throw new InvalidFieldException(field);
    }
    return text;
  }

  static String matching(@Nullable String raw, Pattern pattern, String field) {
    if (raw == null || !pattern.matcher(raw).matches()) {
      throw new InvalidFieldException(field);
    }
    return raw;
  }

  static long between(long value, long min, long max, String field) {
    if (value < min || value > max) {
      throw new InvalidFieldException(field);
    }
    return value;
  }

  static <E extends Enum<E>> E enumValue(Class<E> type, @Nullable String raw, String field) {
    if (raw == null) {
      throw new InvalidFieldException(field);
    }
    try {
      return Enum.valueOf(type, raw);
    } catch (IllegalArgumentException e) {
      throw new InvalidFieldException(field);
    }
  }

  static Currency currency(@Nullable String raw, String field) {
    String code = matching(raw, CURRENCY_CODE, field);
    try {
      return Currency.getInstance(code);
    } catch (IllegalArgumentException e) {
      throw new InvalidFieldException(field);
    }
  }
}
