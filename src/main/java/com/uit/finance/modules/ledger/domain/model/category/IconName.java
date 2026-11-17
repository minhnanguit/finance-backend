package com.uit.finance.modules.ledger.domain.model.category;

import com.uit.finance.modules.ledger.domain.model.shared.Fields;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Tên icon mà mobile map sang hình, {@code [a-z0-9_]}, tối đa 50 ký tự. */
public record IconName(String value) {

  private static final Pattern SHAPE = Pattern.compile("[a-z0-9_]{1,50}");

  public IconName {
    Fields.matching(value, SHAPE, "icon");
  }

  public static @Nullable IconName parse(@Nullable String raw) {
    return raw == null || raw.isBlank() ? null : new IconName(raw.strip());
  }
}
