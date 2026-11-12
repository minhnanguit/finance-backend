package com.uit.finance.modules.budget.domain.model;

import com.uit.finance.modules.budget.domain.exception.BudgetReferencePendingException;
import com.uit.finance.modules.budget.domain.exception.InvalidBudgetCategoryException;
import java.util.Optional;
import java.util.UUID;

/**
 * Những gì ngân sách cần biết về một danh mục của ledger. Application dựng từ {@code CategoryRef};
 * domain không biết ledger tồn tại. Archive không có ở đây vì không chặn gì (ADR-007 G13).
 */
public record CategoryState(boolean expense, boolean topLevel, boolean deleted) {

  /** Còn thoả G2: danh mục chi, cấp 1, chưa xoá. */
  public boolean canHoldBudget() {
    return expense && topLevel && !deleted;
  }

  /**
   * Kiểm danh mục lúc tạo ngân sách. Không thấy (chưa tới hoặc của user khác) là {@code
   * budget.reference_pending} (B2); thấy mà không hợp lệ là {@code budget.invalid_category}.
   */
  public static void requireUsable(UUID categoryId, Optional<CategoryState> state) {
    CategoryState found = state.orElseThrow(() -> new BudgetReferencePendingException(categoryId));
    if (!found.canHoldBudget()) {
      throw new InvalidBudgetCategoryException(categoryId);
    }
  }
}
