package com.uit.finance.modules.ledger.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;

public class InvalidTransferException extends LedgerException {

  public static final String CODE = "ledger.invalid_transfer";

  public InvalidTransferException(String reason) {
    super(ErrorCategory.VALIDATION, CODE, "Invalid transfer: " + reason);
  }
}
