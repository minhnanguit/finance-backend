package com.uit.finance.modules.ledger.domain.exception;

import com.uit.finance.modules.ledger.domain.model.LedgerEntity;
import com.uit.finance.shared.kernel.ErrorCategory;
import java.util.UUID;

/** Gắn bản ghi mới vào ví hoặc danh mục đã archive. */
public class ArchivedException extends LedgerException {

  public static final String CODE = "ledger.archived";

  public ArchivedException(LedgerEntity entity, UUID id) {
    super(ErrorCategory.BUSINESS_RULE, CODE, entity.wireName() + " " + id + " is archived");
  }
}
