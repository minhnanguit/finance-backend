package com.uit.finance.modules.ledger.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Màu dạng {@code #RRGGBB}, lưu chữ hoa để so sánh được. */
public record ColorHex(String value) {

  private static final Pattern SHAPE = Pattern.compile("#[0-9A-F]{6}");

  public ColorHex {
    value = Fields.matching(value == null ? null : value.toUpperCase(Locale.ROOT), SHAPE, "color");
  }

  public static @Nullable ColorHex parse(@Nullable String raw) {
    return raw == null || raw.isBlank() ? null : new ColorHex(raw.strip());
  }
}
