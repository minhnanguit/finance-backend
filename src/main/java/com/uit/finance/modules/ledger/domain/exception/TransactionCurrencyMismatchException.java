package com.uit.finance.modules.ledger.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;

public class TransactionCurrencyMismatchException extends LedgerException {

  public static final String CODE = "ledger.currency_mismatch";

  public TransactionCurrencyMismatchException() {
    super(ErrorCategory.VALIDATION, CODE, "Transaction currency must equal the account currency");
  }
}
