package com.uit.finance.modules.ledger.domain.model.account;

import com.uit.finance.modules.ledger.domain.model.shared.Fields;
import com.uit.finance.shared.kernel.Ensure;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Id ví, do client sinh (ADR-002). */
public record AccountId(UUID value) {

  public AccountId {
    Ensure.notNull(value, "accountId");
  }

  /** Id đến từ client: thiếu là {@code ledger.invalid_field}. */
  public static AccountId of(@Nullable UUID raw) {
    return new AccountId(Fields.required(raw, "accountId"));
  }

  public static @Nullable AccountId ofNullable(@Nullable UUID raw) {
    return raw == null ? null : new AccountId(raw);
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
