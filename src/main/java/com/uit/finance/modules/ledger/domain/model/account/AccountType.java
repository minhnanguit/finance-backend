package com.uit.finance.modules.ledger.domain.model.account;

import com.uit.finance.modules.ledger.domain.model.shared.Fields;
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
