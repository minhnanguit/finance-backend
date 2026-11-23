package com.uit.finance.modules.recurring.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;
import java.util.UUID;

/** Ví đã xoá: không dùng cho phiên bản thêm hoặc sửa (ADR-009 RS13). */
public class InvalidRecurringAccountException extends RecurringException {

  public static final String CODE = "recurring.invalid_account";

  public InvalidRecurringAccountException(UUID id) {
    super(ErrorCategory.VALIDATION, CODE, "Account " + id + " cannot be used");
  }
}
