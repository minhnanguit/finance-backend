package com.uit.finance.modules.recurring.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;
import java.util.UUID;

/** Không có, hoặc là của user khác (ADR-006 B2). */
public class RecurringNotFoundException extends RecurringException {

  public static final String CODE = "recurring.not_found";

  public RecurringNotFoundException(UUID id) {
    super(ErrorCategory.NOT_FOUND, CODE, "Recurring " + id + " not found");
  }
}
