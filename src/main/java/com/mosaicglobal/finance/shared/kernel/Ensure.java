package com.mosaicglobal.finance.shared.kernel;

/** Small guard helpers for value objects. Throws {@link IllegalArgumentException} on violation. */
public final class Ensure {

  private Ensure() {}

  public static String notBlank(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }

  public static String maxLength(String value, int max, String field) {
    if (value != null && value.length() > max) {
      throw new IllegalArgumentException(field + " must be at most " + max + " characters");
    }
    return value;
  }

  public static String lengthBetween(String value, int min, int max, String field) {
    notBlank(value, field);
    if (value.length() < min || value.length() > max) {
      throw new IllegalArgumentException(
          field + " must be between " + min + " and " + max + " characters");
    }
    return value;
  }

  public static <T> T notNull(T value, String field) {
    if (value == null) {
      throw new IllegalArgumentException(field + " must not be null");
    }
    return value;
  }
}
