package com.uit.finance.modules.budget.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;
import java.util.UUID;

/** Danh mục của chính user đã xoá, không phải danh mục chi, hoặc không phải cấp 1 (ADR-007 G2). */
public class InvalidBudgetCategoryException extends BudgetException {

  public static final String CODE = "budget.invalid_category";

  public InvalidBudgetCategoryException(UUID categoryId) {
    super(
        ErrorCategory.VALIDATION,
        CODE,
        "Category " + categoryId + " must be a live top-level expense category");
  }
}
