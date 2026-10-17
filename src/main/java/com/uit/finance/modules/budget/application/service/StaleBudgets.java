package com.uit.finance.modules.budget.application.service;

import com.uit.finance.modules.budget.application.port.out.SaveBudgetPort;
import com.uit.finance.modules.budget.domain.model.Budget;
import com.uit.finance.modules.budget.domain.model.CategoryState;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Xoá mềm ngân sách mà danh mục không còn thoả G2 (đã xoá, không còn cấp 1), dựa trên trạng thái
 * **hiện tại** của danh mục chứ không dựa vào event (ADR-007 GS6, GS8).
 *
 * <p>Danh mục không thấy thì giữ ngân sách: thiếu thông tin thì không xoá. Phải chạy dưới {@code
 * BudgetWriteLockPort.lock} của caller.
 */
final class StaleBudgets {

  private final BudgetCategories categories;
  private final SaveBudgetPort save;

  StaleBudgets(BudgetCategories categories, SaveBudgetPort save) {
    this.categories = categories;
    this.save = save;
  }

  /** Mọi ngân sách trong {@code budgets} cùng trỏ tới {@code categoryId}. */
  int purgeOfCategory(UserId owner, UUID categoryId, List<Budget> budgets, Instant now) {
    Optional<CategoryState> state = categories.state(owner, categoryId);
    if (state.isEmpty() || state.get().canHoldBudget()) {
      return 0;
    }
    return deleteAll(owner, budgets, now);
  }

  /** Đối chiếu cả lô bằng một query sang ledger. */
  int purge(UserId owner, List<Budget> budgets, Instant now) {
    Set<UUID> ids =
        budgets.stream().map(budget -> budget.getTerms().categoryId()).collect(Collectors.toSet());
    Map<UUID, CategoryState> states = categories.states(owner, ids);
    List<Budget> stale =
        budgets.stream()
            .filter(
                budget -> {
                  CategoryState state = states.get(budget.getTerms().categoryId());
                  return state != null && !state.canHoldBudget();
                })
            .toList();
    return deleteAll(owner, stale, now);
  }

  private int deleteAll(UserId owner, List<Budget> budgets, Instant now) {
    int deleted = 0;
    for (Budget budget : budgets) {
      if (budget.delete(now)) {
        save.update(owner, budget);
        deleted++;
      }
    }
    return deleted;
  }
}
