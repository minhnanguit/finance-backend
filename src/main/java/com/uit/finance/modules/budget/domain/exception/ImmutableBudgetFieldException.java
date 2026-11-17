package com.uit.finance.modules.budget.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;

/** Đổi {@code categoryId}, {@code currency} hoặc {@code startMonth} sau khi tạo (ADR-007 G8). */
public class ImmutableBudgetFieldException extends BudgetException {

  public static final String CODE = "budget.immutable_field";

  private final String field;

  public ImmutableBudgetFieldException(String field) {
    super(ErrorCategory.VALIDATION, CODE, "Field '" + field + "' cannot change after creation");
    this.field = field;
  }

  public String field() {
    return field;
  }
}
