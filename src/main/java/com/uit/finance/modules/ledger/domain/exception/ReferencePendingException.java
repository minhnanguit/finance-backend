package com.uit.finance.modules.ledger.domain.exception;

import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.shared.kernel.ErrorCategory;
import java.util.UUID;

/**
 * Ví hoặc danh mục được trỏ tới chưa thấy: có thể chưa sync tới, có thể là của user khác. Hai
 * trường hợp trả y hệt nhau (ADR-006 B2); sync đổi thành {@code RETRY} (ADR-002 §4).
 */
public class ReferencePendingException extends LedgerException {

  public static final String CODE = "ledger.reference_pending";

  public ReferencePendingException(LedgerEntity entity, UUID id) {
    super(
        ErrorCategory.BUSINESS_RULE,
        CODE,
        "Referenced " + entity.wireName() + " " + id + " is not available yet");
  }
}
