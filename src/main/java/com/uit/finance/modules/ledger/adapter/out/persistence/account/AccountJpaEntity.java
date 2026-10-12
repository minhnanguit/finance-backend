package com.uit.finance.modules.ledger.adapter.out.persistence.account;

import com.uit.finance.modules.ledger.domain.model.account.AccountType;
import com.uit.finance.shared.persistence.AbstractJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Dòng của bảng {@code accounts}. Chỉ được load và sửa qua JPA; insert đi bằng SQL ({@code ON
 * CONFLICT DO NOTHING}) trong {@link AccountPersistenceAdapter}, nên không có constructor công
 * khai.
 */
@Entity
@Table(name = "accounts")
class AccountJpaEntity extends AbstractJpaEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(nullable = false, length = 50)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AccountType type;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "opening_balance_minor", nullable = false)
  private long openingBalanceMinor;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "archived_at")
  private @Nullable Instant archivedAt;

  @Column(name = "change_seq", nullable = false)
  private long changeSeq;

  protected AccountJpaEntity() {}

  UUID getUserId() {
    return userId;
  }

  String getName() {
    return name;
  }

  AccountType getType() {
    return type;
  }

  String getCurrency() {
    return currency;
  }

  long getOpeningBalanceMinor() {
    return openingBalanceMinor;
  }

  int getSortOrder() {
    return sortOrder;
  }

  @Nullable Instant getArchivedAt() {
    return archivedAt;
  }

  long getChangeSeq() {
    return changeSeq;
  }

  /** {@code userId} và {@code id} không bao giờ đổi. */
  void apply(
      String name,
      AccountType type,
      String currency,
      long openingBalanceMinor,
      int sortOrder,
      @Nullable Instant archivedAt) {
    this.name = name;
    this.type = type;
    this.currency = currency;
    this.openingBalanceMinor = openingBalanceMinor;
    this.sortOrder = sortOrder;
    this.archivedAt = archivedAt;
  }

  void assignChangeSeq(long changeSeq) {
    this.changeSeq = changeSeq;
  }
}
