package com.uit.finance.modules.recurring.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;

/** Field thiếu, sai hoặc ngoài giới hạn. Chỉ kèm tên field, không kèm giá trị (B8). */
public class InvalidRecurringFieldException extends RecurringException {

  public static final String CODE = "recurring.invalid_field";

  private final String field;

  public InvalidRecurringFieldException(String field) {
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
