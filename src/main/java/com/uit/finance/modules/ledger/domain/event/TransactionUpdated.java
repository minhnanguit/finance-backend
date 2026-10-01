package com.uit.finance.modules.ledger.domain.event;

import com.uit.finance.modules.ledger.domain.model.transaction.Transaction;
import com.uit.finance.shared.kernel.DomainEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Giao dịch đã bị sửa. {@code accountId} là ví sau khi sửa. Routing key {@value #TYPE}. */
public record TransactionUpdated(
    UUID eventId,
    Instant occurredAt,
    UUID transactionId,
    UUID userId,
    UUID accountId,
    LocalDate occurredOn)
    implements DomainEvent {

  public static final String TYPE = "ledger.transaction.updated";

  public static TransactionUpdated of(Transaction transaction, Instant now) {
    return new TransactionUpdated(
        UUID.randomUUID(),
        now,
        transaction.getId().value(),
        transaction.getOwner().value(),
        transaction.getDetails().accountId().value(),
        transaction.getDetails().occurredOn());
  }

  @Override
  public String type() {
    return TYPE;
  }
}
