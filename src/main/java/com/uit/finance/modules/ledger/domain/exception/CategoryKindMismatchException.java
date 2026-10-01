package com.uit.finance.modules.ledger.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;

public class CategoryKindMismatchException extends LedgerException {

  public static final String CODE = "ledger.category_kind_mismatch";

  public CategoryKindMismatchException() {
    super(ErrorCategory.VALIDATION, CODE, "Category kind does not match the transaction type");
  }
}
