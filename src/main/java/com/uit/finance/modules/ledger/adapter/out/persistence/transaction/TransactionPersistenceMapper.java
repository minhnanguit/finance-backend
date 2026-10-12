package com.uit.finance.modules.ledger.adapter.out.persistence.transaction;

import com.uit.finance.modules.ledger.adapter.out.persistence.support.SqlValues;
import com.uit.finance.modules.ledger.domain.model.account.AccountId;
import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import com.uit.finance.modules.ledger.domain.model.transaction.Note;
import com.uit.finance.modules.ledger.domain.model.transaction.Payee;
import com.uit.finance.modules.ledger.domain.model.transaction.Transaction;
import com.uit.finance.modules.ledger.domain.model.transaction.TransactionDetails;
import com.uit.finance.modules.ledger.domain.model.transaction.TransactionId;
import com.uit.finance.shared.kernel.Money;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

final class TransactionPersistenceMapper {

  private TransactionPersistenceMapper() {}

  static Transaction toDomain(TransactionJpaEntity entity) {
    return Transaction.rehydrate(
        new TransactionId(entity.getId()),
        new UserId(entity.getUserId()),
        new TransactionDetails(
            entity.getType(),
            entity.getStatus(),
            new AccountId(entity.getAccountId()),
            Optional.ofNullable(entity.getCounterAccountId()).map(AccountId::new).orElse(null),
            Money.of(entity.getAmountMinor(), entity.getCurrency()),
            Optional.ofNullable(entity.getCategoryId()).map(CategoryId::new).orElse(null),
            entity.getOccurredOn(),
            entity.getOccurredAt(),
            Optional.ofNullable(entity.getPayee()).map(Payee::new).orElse(null),
            Optional.ofNullable(entity.getNote()).map(Note::new).orElse(null)),
        entity.getDeletedAt());
  }

  static void apply(Transaction transaction, TransactionJpaEntity entity) {
    TransactionDetails details = transaction.getDetails();
    entity.apply(
        details.type(),
        details.status(),
        details.accountId().value(),
        counterAccountId(details),
        details.amount().amountMinor(),
        details.amount().currencyCode(),
        categoryId(details),
        details.occurredOn(),
        details.occurredAt(),
        payee(details),
        note(details));
    if (transaction.getDeletedAt() != null && !entity.isDeleted()) {
      entity.markDeleted(transaction.getDeletedAt());
    }
  }

  /** Tham số cho {@code TransactionPersistenceAdapter.INSERT}. */
  static MapSqlParameterSource insertParams(Transaction transaction, long changeSeq, Instant now) {
    TransactionDetails details = transaction.getDetails();
    return new SqlValues()
        .uuid("id", transaction.getId().value())
        .uuid("userId", transaction.getOwner().value())
        .text("type", details.type().name())
        .text("status", details.status().name())
        .uuid("accountId", details.accountId().value())
        .uuid("counterAccountId", counterAccountId(details))
        .number("amountMinor", details.amount().amountMinor())
        .text("currency", details.amount().currencyCode())
        .uuid("categoryId", categoryId(details))
        .date("occurredOn", details.occurredOn())
        .timestamp("occurredAt", details.occurredAt())
        .text("payee", payee(details))
        .text("note", note(details))
        .number("changeSeq", changeSeq)
        .timestamp("now", now)
        .timestamp("deletedAt", transaction.getDeletedAt())
        .build();
  }

  private static @Nullable UUID counterAccountId(TransactionDetails details) {
    return details.counterAccountId() == null ? null : details.counterAccountId().value();
  }

  private static @Nullable UUID categoryId(TransactionDetails details) {
    return details.categoryId() == null ? null : details.categoryId().value();
  }

  private static @Nullable String payee(TransactionDetails details) {
    return details.payee() == null ? null : details.payee().value();
  }

  private static @Nullable String note(TransactionDetails details) {
    return details.note() == null ? null : details.note().value();
  }
}
