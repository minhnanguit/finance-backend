package com.uit.finance.modules.ledger.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;

public class InvalidParentException extends LedgerException {

  public static final String CODE = "ledger.invalid_parent";

  public InvalidParentException(String reason) {
    super(ErrorCategory.VALIDATION, CODE, "Invalid parent category: " + reason);
  }
}
