package com.uit.finance.modules.budget.domain.exception;

import com.uit.finance.shared.kernel.DomainException;
import com.uit.finance.shared.kernel.ErrorCategory;

/**
 * Gốc của mọi lỗi nghiệp vụ ngân sách. Mã lỗi ổn định dạng {@code budget.<reason>} (ADR-007 §3);
 * sync dựa vào mã để trả {@code REJECTED}, {@code RETRY} hay {@code CONFLICT}.
 *
 * <p>Message chỉ chứa id và tên field, không bao giờ chứa số tiền (ADR-006 B8).
 */
public abstract class BudgetException extends DomainException {

  protected BudgetException(ErrorCategory category, String code, String message) {
    super(category, code, message);
  }
}
