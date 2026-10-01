package com.uit.finance.modules.ledger.domain.model;

import com.uit.finance.shared.kernel.Ensure;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Id giao dịch, do client sinh (ADR-002). */
public record TransactionId(UUID value) {

  public TransactionId {
    Ensure.notNull(value, "transactionId");
  }

  /** Id đến từ client: thiếu là {@code ledger.invalid_field}. */
  public static TransactionId of(@Nullable UUID raw) {
    return new TransactionId(Fields.required(raw, "transactionId"));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
