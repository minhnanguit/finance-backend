package com.uit.finance.modules.ledger.domain.exception;

import com.uit.finance.modules.ledger.domain.model.LedgerEntity;
import com.uit.finance.shared.kernel.ErrorCategory;
import java.util.UUID;

/** Xoá ví hoặc danh mục đang được dùng. Chỉ archive được (ADR-005 D7). */
public class InUseException extends LedgerException {

  public static final String CODE = "ledger.in_use";

  public InUseException(LedgerEntity entity, UUID id) {
    super(
        ErrorCategory.BUSINESS_RULE,
        CODE,
        entity.wireName() + " " + id + " is still in use; archive it instead");
  }
}
