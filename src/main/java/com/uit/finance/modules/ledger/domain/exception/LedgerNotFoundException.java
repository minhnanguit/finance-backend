package com.uit.finance.modules.ledger.domain.exception;

import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.shared.kernel.ErrorCategory;
import java.util.UUID;

/**
 * Bản ghi không tồn tại, là của user khác, hoặc đã xoá. Ba trường hợp trả cùng một lỗi để không dò
 * được id nào có thật (ADR-006 B1, B2).
 */
public class LedgerNotFoundException extends LedgerException {

  public static final String CODE = "ledger.not_found";

  public LedgerNotFoundException(LedgerEntity entity, UUID id) {
    super(ErrorCategory.NOT_FOUND, CODE, entity.wireName() + " " + id + " does not exist");
  }
}
