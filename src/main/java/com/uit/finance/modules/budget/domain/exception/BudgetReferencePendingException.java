package com.uit.finance.modules.budget.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;
import java.util.UUID;

/**
 * Danh mục được trỏ tới chưa thấy: chưa sync tới, hoặc là của user khác. Hai trường hợp trả y hệt
 * nhau (ADR-006 B2); sync đổi thành {@code RETRY}.
 */
public class BudgetReferencePendingException extends BudgetException {

  public static final String CODE = "budget.reference_pending";

  public BudgetReferencePendingException(UUID categoryId) {
    super(
        ErrorCategory.BUSINESS_RULE,
        CODE,
        "Referenced category " + categoryId + " is not available yet");
  }
}
