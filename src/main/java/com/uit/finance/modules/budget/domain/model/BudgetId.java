package com.uit.finance.modules.budget.domain.model;

import com.uit.finance.modules.budget.domain.exception.InvalidBudgetFieldException;
import com.uit.finance.shared.kernel.Ensure;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Id ngân sách, UUIDv4 do client sinh (ADR-007 GS2). */
public record BudgetId(UUID value) {

  public BudgetId {
    Ensure.notNull(value, "budgetId");
  }

  /** Id đến từ client: thiếu là {@code budget.invalid_field}. */
  public static BudgetId of(@Nullable UUID raw) {
    if (raw == null) {
      throw new InvalidBudgetFieldException("id");
    }
    return new BudgetId(raw);
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
