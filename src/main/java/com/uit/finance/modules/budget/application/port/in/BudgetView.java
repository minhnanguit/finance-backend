package com.uit.finance.modules.budget.application.port.in;

import com.uit.finance.modules.budget.domain.model.Budget;
import com.uit.finance.modules.budget.domain.model.BudgetTerms;
import java.time.LocalDate;
import java.util.UUID;

/** Bản ghi ngân sách hiện tại trên server. */
public record BudgetView(
    UUID id,
    UUID categoryId,
    String currency,
    long amountMinor,
    LocalDate startMonth,
    boolean deleted) {

  public static BudgetView from(Budget budget) {
    BudgetTerms terms = budget.getTerms();
    return new BudgetView(
        budget.getId().value(),
        terms.categoryId(),
        terms.currency().getCurrencyCode(),
        terms.amount().minor(),
        terms.startMonth().firstDay(),
        budget.isDeleted());
  }

  /** Không in số tiền (ADR-006 B8). */
  @Override
  public String toString() {
    return "BudgetView[id=%s, categoryId=%s, currency=%s, deleted=%s]"
        .formatted(id, categoryId, currency, deleted);
  }
}
