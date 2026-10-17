package com.uit.finance.modules.budget.adapter.out.persistence;

import com.uit.finance.shared.persistence.AbstractJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** Dòng của bảng {@code budgets}. Insert đi bằng SQL trong {@link BudgetPersistenceAdapter}. */
@Entity
@Table(name = "budgets")
class BudgetJpaEntity extends AbstractJpaEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "category_id", nullable = false, updatable = false)
  private UUID categoryId;

  @Column(nullable = false, updatable = false, length = 3)
  private String currency;

  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;

  @Column(name = "start_month", nullable = false, updatable = false)
  private LocalDate startMonth;

  @Column(name = "change_seq", nullable = false)
  private long changeSeq;

  protected BudgetJpaEntity() {}

  UUID getUserId() {
    return userId;
  }

  UUID getCategoryId() {
    return categoryId;
  }

  String getCurrency() {
    return currency;
  }

  long getAmountMinor() {
    return amountMinor;
  }

  LocalDate getStartMonth() {
    return startMonth;
  }

  long getChangeSeq() {
    return changeSeq;
  }

  /** {@code categoryId}, {@code currency}, {@code startMonth} bất biến (G8) nên không có ở đây. */
  void applyAmount(long amountMinor) {
    this.amountMinor = amountMinor;
  }

  void assignChangeSeq(long changeSeq) {
    this.changeSeq = changeSeq;
  }
}
