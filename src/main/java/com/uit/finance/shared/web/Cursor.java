package com.uit.finance.shared.web;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Opaque cursor codec so clients never depend on the underlying sort key. */
public final class Cursor {

  private Cursor() {}

  public static String encode(String rawPosition) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(rawPosition.getBytes(StandardCharsets.UTF_8));
  }

  public static String decode(String cursor) {
    try {
      return new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("cursor is malformed", e);
    }
  }
}
