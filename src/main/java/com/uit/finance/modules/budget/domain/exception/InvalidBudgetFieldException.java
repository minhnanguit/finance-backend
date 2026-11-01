package com.uit.finance.modules.budget.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;

/** Field thiếu, sai định dạng hoặc ngoài giới hạn. Chỉ kèm tên field, không kèm giá trị (B8). */
public class InvalidBudgetFieldException extends BudgetException {

  public static final String CODE = "budget.invalid_field";

  private final String field;

  public InvalidBudgetFieldException(String field) {
    super(
        ErrorCategory.VALIDATION,
        CODE,
        "Field '" + field + "' is missing, malformed or out of range");
    this.field = field;
  }

  public String field() {
    return field;
  }
}
