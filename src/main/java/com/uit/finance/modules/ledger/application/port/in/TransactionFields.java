package com.uit.finance.modules.ledger.application.port.in;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Dữ liệu giao dịch client gửi lên, chưa kiểm tra. */
public record TransactionFields(
    @Nullable String type,
    @Nullable String status,
    @Nullable UUID accountId,
    @Nullable UUID counterAccountId,
    long amountMinor,
    @Nullable String currency,
    @Nullable UUID categoryId,
    @Nullable LocalDate occurredOn,
    @Nullable Instant occurredAt,
    @Nullable String payee,
    @Nullable String note) {

  /** Không in số tiền, người nhận, ghi chú (ADR-006 B8). */
  @Override
  public String toString() {
    return "TransactionFields[type=%s, status=%s, accountId=%s]".formatted(type, status, accountId);
  }
}
