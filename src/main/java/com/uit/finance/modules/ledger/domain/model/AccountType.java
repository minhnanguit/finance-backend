package com.uit.finance.modules.ledger.domain.model;

import org.jspecify.annotations.Nullable;

public enum AccountType {
  CASH,
  BANK,
  EWALLET,
  OTHER;

  public static AccountType parse(@Nullable String raw) {
    return Fields.enumValue(AccountType.class, raw, "type");
  }
}
