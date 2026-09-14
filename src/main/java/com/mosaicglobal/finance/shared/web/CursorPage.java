package com.mosaicglobal.finance.shared.web;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Cursor-based page: {@code nextCursor} is null when there is nothing more to fetch. */
public record CursorPage<T>(List<T> items, @Nullable String nextCursor) {

  public static <T> CursorPage<T> last(List<T> items) {
    return new CursorPage<>(items, null);
  }
}
