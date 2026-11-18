package com.uit.finance.modules.recurring.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;

/** Đổi field bất biến, sửa phiên bản đã hiệu lực, mở lại khoản đã kết thúc (ADR-009 R10, R11). */
public class ImmutableRecurringFieldException extends RecurringException {

  public static final String CODE = "recurring.immutable_field";

  private final String field;

  public ImmutableRecurringFieldException(String field) {
    super(ErrorCategory.VALIDATION, CODE, "Field '" + field + "' cannot change this way");
    this.field = field;
  }

  public String field() {
    return field;
  }
}
