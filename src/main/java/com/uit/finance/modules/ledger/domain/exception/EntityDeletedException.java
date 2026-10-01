package com.uit.finance.modules.ledger.domain.exception;

import com.uit.finance.modules.ledger.domain.model.LedgerEntity;
import com.uit.finance.shared.kernel.ErrorCategory;
import java.util.UUID;

/** Sửa bản ghi đã xoá. Xoá luôn thắng (ADR-002 S4); sync đổi thành {@code CONFLICT}. */
public class EntityDeletedException extends LedgerException {

  public static final String CODE = "ledger.deleted";

  public EntityDeletedException(LedgerEntity entity, UUID id) {
    super(ErrorCategory.CONFLICT, CODE, entity.wireName() + " " + id + " has been deleted");
  }
}
