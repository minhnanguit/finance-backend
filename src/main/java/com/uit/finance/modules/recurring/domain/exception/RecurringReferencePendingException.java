package com.uit.finance.modules.recurring.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;
import java.util.UUID;

/** Ví hoặc danh mục chưa tới, hoặc của user khác: không phân biệt được (B2). */
public class RecurringReferencePendingException extends RecurringException {

  public static final String CODE = "recurring.reference_pending";

  public RecurringReferencePendingException(UUID id) {
    super(ErrorCategory.BUSINESS_RULE, CODE, "Reference " + id + " not available yet");
  }
}
