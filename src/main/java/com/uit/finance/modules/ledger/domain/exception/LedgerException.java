package com.uit.finance.modules.ledger.domain.exception;

import com.uit.finance.shared.kernel.DomainException;
import com.uit.finance.shared.kernel.ErrorCategory;

/**
 * Gốc của mọi lỗi nghiệp vụ ledger. Mã lỗi ổn định dạng {@code ledger.<reason>} (ADR-005 §3); sync
 * dựa vào mã để trả {@code REJECTED}, {@code RETRY} hay {@code CONFLICT}.
 *
 * <p>Message chỉ chứa id và tên field, không bao giờ chứa số tiền, ghi chú, người nhận hay tên
 * (ADR-006 B8), vì message có thể đi vào log và Sentry.
 */
public abstract class LedgerException extends DomainException {

  protected LedgerException(ErrorCategory category, String code, String message) {
    super(category, code, message);
  }
}
