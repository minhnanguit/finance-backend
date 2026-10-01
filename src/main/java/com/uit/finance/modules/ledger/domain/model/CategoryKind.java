package com.uit.finance.modules.ledger.domain.model;

import org.jspecify.annotations.Nullable;

public enum CategoryKind {
  INCOME,
  EXPENSE;

  public static CategoryKind parse(@Nullable String raw) {
    return Fields.enumValue(CategoryKind.class, raw, "kind");
  }
}
