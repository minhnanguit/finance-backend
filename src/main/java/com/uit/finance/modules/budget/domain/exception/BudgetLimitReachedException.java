package com.uit.finance.modules.budget.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;

/** Vượt số ngân sách hợp lệ tối đa của một user (ADR-007 §4). */
public class BudgetLimitReachedException extends BudgetException {

  public static final String CODE = "budget.limit_reached";

  public BudgetLimitReachedException(int max) {
    super(ErrorCategory.BUSINESS_RULE, CODE, "A user can have at most " + max + " budgets");
  }
}
