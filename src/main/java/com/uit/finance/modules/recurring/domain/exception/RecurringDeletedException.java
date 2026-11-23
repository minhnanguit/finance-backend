package com.uit.finance.modules.recurring.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;
import java.util.UUID;

/** Sửa khoản đã xoá: xoá luôn thắng (ADR-002 S4). */
public class RecurringDeletedException extends RecurringException {

  public static final String CODE = "recurring.deleted";

  public RecurringDeletedException(UUID id) {
    super(ErrorCategory.CONFLICT, CODE, "Recurring " + id + " is deleted");
  }
}
