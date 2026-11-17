package com.uit.finance.modules.budget.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;
import java.util.UUID;

/** Cặp (danh mục, tiền tệ) đã có ngân sách chưa xoá (ADR-007 G3, GS4). */
public class DuplicateBudgetException extends BudgetException {

  public static final String CODE = "budget.duplicate";

  public DuplicateBudgetException(UUID categoryId) {
    super(
        ErrorCategory.CONFLICT,
        CODE,
        "Category " + categoryId + " already has a budget in this currency");
  }
}
