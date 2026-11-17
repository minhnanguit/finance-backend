package com.uit.finance.modules.ledger.application.port.in.transaction;

import com.uit.finance.modules.ledger.domain.model.account.AccountId;
import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import com.uit.finance.modules.ledger.domain.model.transaction.Note;
import com.uit.finance.modules.ledger.domain.model.transaction.Payee;
import com.uit.finance.modules.ledger.domain.model.transaction.Transaction;
import com.uit.finance.modules.ledger.domain.model.transaction.TransactionDetails;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Bản ghi giao dịch hiện tại trên server. */
public record TransactionView(
    UUID id,
    String type,
    String status,
    UUID accountId,
    @Nullable UUID counterAccountId,
    long amountMinor,
    String currency,
    @Nullable UUID categoryId,
    LocalDate occurredOn,
    @Nullable Instant occurredAt,
    @Nullable String payee,
    @Nullable String note,
    boolean deleted) {

  public static TransactionView from(Transaction transaction) {
    TransactionDetails details = transaction.getDetails();
    return new TransactionView(
        transaction.getId().value(),
        details.type().name(),
        details.status().name(),
        details.accountId().value(),
        Optional.ofNullable(details.counterAccountId()).map(AccountId::value).orElse(null),
        details.amount().amountMinor(),
        details.amount().currencyCode(),
        Optional.ofNullable(details.categoryId()).map(CategoryId::value).orElse(null),
        details.occurredOn(),
        details.occurredAt(),
        Optional.ofNullable(details.payee()).map(Payee::value).orElse(null),
        Optional.ofNullable(details.note()).map(Note::value).orElse(null),
        transaction.isDeleted());
  }

  /** Không in số tiền, người nhận, ghi chú (ADR-006 B8). */
  @Override
  public String toString() {
    return "TransactionView[id=%s, type=%s, deleted=%s]".formatted(id, type, deleted);
  }
}
