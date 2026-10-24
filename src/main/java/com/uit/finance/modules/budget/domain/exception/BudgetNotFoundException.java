package com.uit.finance.modules.budget.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;
import java.util.UUID;

/**
 * Ngân sách không tồn tại hoặc là của user khác. Hai trường hợp trả cùng một lỗi để không dò được
 * id nào có thật (ADR-006 B1, B2).
 */
public class BudgetNotFoundException extends BudgetException {

  public static final String CODE = "budget.not_found";

  public BudgetNotFoundException(UUID id) {
    super(ErrorCategory.NOT_FOUND, CODE, "budget " + id + " does not exist");
  }
}
