package com.uit.finance.modules.ledger.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;
import java.util.UUID;

public class KindImmutableException extends LedgerException {

  public static final String CODE = "ledger.kind_immutable";

  public KindImmutableException(UUID categoryId) {
    super(ErrorCategory.VALIDATION, CODE, "The kind of category " + categoryId + " cannot change");
  }
}
