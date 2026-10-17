package com.uit.finance.modules.budget.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;
import java.util.UUID;

/** Sửa ngân sách đã xoá. Xoá luôn thắng (ADR-002 S4); sync đổi thành {@code CONFLICT}. */
public class BudgetDeletedException extends BudgetException {

  public static final String CODE = "budget.deleted";

  public BudgetDeletedException(UUID id) {
    super(ErrorCategory.CONFLICT, CODE, "budget " + id + " has been deleted");
  }
}
