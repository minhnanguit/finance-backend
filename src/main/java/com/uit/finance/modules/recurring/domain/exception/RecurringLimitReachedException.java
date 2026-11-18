package com.uit.finance.modules.recurring.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;

/** Quá số khoản còn hiệu lực mỗi user (ADR-009 §7). */
public class RecurringLimitReachedException extends RecurringException {

  public static final String CODE = "recurring.limit_reached";

  public RecurringLimitReachedException(int max) {
    super(ErrorCategory.BUSINESS_RULE, CODE, "At most " + max + " active recurrings");
  }
}
