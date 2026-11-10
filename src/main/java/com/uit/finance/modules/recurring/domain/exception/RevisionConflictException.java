package com.uit.finance.modules.recurring.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;
import java.util.UUID;

/** {@code revision} client dựa vào khác server (ADR-008 RS8). */
public class RevisionConflictException extends RecurringException {

  public static final String CODE = "recurring.revision_conflict";

  public RevisionConflictException(UUID id) {
    super(ErrorCategory.CONFLICT, CODE, "Recurring " + id + " changed on the server");
  }
}
