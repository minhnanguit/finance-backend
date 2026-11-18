package com.uit.finance.modules.recurring.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;
import java.util.UUID;

/** Ví khác tiền tệ của khoản (ADR-009 R12). */
public class RecurringCurrencyMismatchException extends RecurringException {

  public static final String CODE = "recurring.currency_mismatch";

  public RecurringCurrencyMismatchException(UUID id) {
    super(ErrorCategory.VALIDATION, CODE, "Account " + id + " has another currency");
  }
}
