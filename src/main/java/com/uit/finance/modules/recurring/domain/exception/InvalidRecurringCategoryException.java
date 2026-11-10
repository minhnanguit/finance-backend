package com.uit.finance.modules.recurring.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;
import java.util.UUID;

/** Danh mục đã xoá hoặc khác loại với khoản (ADR-008 R12). */
public class InvalidRecurringCategoryException extends RecurringException {

  public static final String CODE = "recurring.invalid_category";

  public InvalidRecurringCategoryException(UUID id) {
    super(ErrorCategory.VALIDATION, CODE, "Category " + id + " cannot be used");
  }
}
