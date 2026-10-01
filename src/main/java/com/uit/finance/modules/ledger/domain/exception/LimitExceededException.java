package com.uit.finance.modules.ledger.domain.exception;

import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.shared.kernel.ErrorCategory;

/** Vượt số ví hoặc danh mục tối đa của một user (ADR-006 B4). */
public class LimitExceededException extends LedgerException {

  public static final String CODE = "ledger.limit_exceeded";

  public LimitExceededException(LedgerEntity entity, int max) {
    super(
        ErrorCategory.BUSINESS_RULE,
        CODE,
        "A user can have at most " + max + " " + entity.wireName() + " records");
  }
}
