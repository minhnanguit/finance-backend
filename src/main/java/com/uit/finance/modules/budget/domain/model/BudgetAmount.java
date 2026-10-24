package com.uit.finance.modules.budget.domain.model;

import static com.uit.finance.modules.budget.domain.model.BudgetLimits.MAX_AMOUNT_MINOR;

import com.uit.finance.modules.budget.domain.exception.InvalidBudgetFieldException;

/** Mức chi tối đa mỗi tháng, đơn vị nhỏ nhất của tiền tệ (ADR-001), từ 1 tới 10¹⁵. */
public record BudgetAmount(long minor) {

  public BudgetAmount {
    if (minor < 1 || minor > MAX_AMOUNT_MINOR) {
      throw new InvalidBudgetFieldException("amountMinor");
    }
  }

  /** Không in số tiền (ADR-006 B8). */
  @Override
  public String toString() {
    return "BudgetAmount[***]";
  }
}
