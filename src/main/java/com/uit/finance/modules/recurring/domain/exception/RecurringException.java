package com.uit.finance.modules.recurring.domain.exception;

import com.uit.finance.shared.kernel.DomainException;
import com.uit.finance.shared.kernel.ErrorCategory;

/**
 * Gốc của mọi lỗi nghiệp vụ khoản định kỳ. Mã ổn định dạng {@code recurring.<reason>} (ADR-009 §6);
 * sync dựa vào mã để trả {@code REJECTED}, {@code RETRY} hay {@code CONFLICT}.
 *
 * <p>Message chỉ chứa id và tên field, không bao giờ chứa tên khoản, số tiền, ghi chú (ADR-006 B8).
 */
public abstract class RecurringException extends DomainException {

  protected RecurringException(ErrorCategory category, String code, String message) {
    super(category, code, message);
  }
}
