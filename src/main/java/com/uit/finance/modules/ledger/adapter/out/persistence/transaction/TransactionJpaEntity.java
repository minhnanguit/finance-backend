package com.uit.finance.modules.ledger.adapter.out.persistence.transaction;

import com.uit.finance.modules.ledger.domain.model.transaction.TransactionStatus;
import com.uit.finance.modules.ledger.domain.model.transaction.TransactionType;
import com.uit.finance.shared.persistence.AbstractJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Dòng của bảng {@code transactions}. Insert đi bằng SQL trong {@link
 * TransactionPersistenceAdapter}.
 */
@Entity
@Table(name = "transactions")
class TransactionJpaEntity extends AbstractJpaEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TransactionType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TransactionStatus status;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "counter_account_id")
  private @Nullable UUID counterAccountId;

  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "category_id")
  private @Nullable UUID categoryId;

  @Column(name = "occurred_on", nullable = false)
  private LocalDate occurredOn;

  @Column(name = "occurred_at")
  private @Nullable Instant occurredAt;

  @Column(length = 100)
  private @Nullable String payee;

  @Column(length = 500)
  private @Nullable String note;

  @Column(name = "change_seq", nullable = false)
  private long changeSeq;

  protected TransactionJpaEntity() {}

  UUID getUserId() {
    return userId;
  }

  TransactionType getType() {
    return type;
  }

  TransactionStatus getStatus() {
    return status;
  }

  UUID getAccountId() {
    return accountId;
  }

  @Nullable UUID getCounterAccountId() {
    return counterAccountId;
  }

  long getAmountMinor() {
    return amountMinor;
  }

  String getCurrency() {
    return currency;
  }

  @Nullable UUID getCategoryId() {
    return categoryId;
  }

  LocalDate getOccurredOn() {
    return occurredOn;
  }

  @Nullable Instant getOccurredAt() {
    return occurredAt;
  }

  @Nullable String getPayee() {
    return payee;
  }

  @Nullable String getNote() {
    return note;
  }

  long getChangeSeq() {
    return changeSeq;
  }

  void apply(
      TransactionType type,
      TransactionStatus status,
      UUID accountId,
      @Nullable UUID counterAccountId,
      long amountMinor,
      String currency,
      @Nullable UUID categoryId,
      LocalDate occurredOn,
      @Nullable Instant occurredAt,
      @Nullable String payee,
      @Nullable String note) {
    this.type = type;
    this.status = status;
    this.accountId = accountId;
    this.counterAccountId = counterAccountId;
    this.amountMinor = amountMinor;
    this.currency = currency;
    this.categoryId = categoryId;
    this.occurredOn = occurredOn;
    this.occurredAt = occurredAt;
    this.payee = payee;
    this.note = note;
  }

  void assignChangeSeq(long changeSeq) {
    this.changeSeq = changeSeq;
  }
}
