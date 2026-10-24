package com.uit.finance.modules.budget.application.service;

import com.uit.finance.modules.budget.domain.model.CategoryState;
import com.uit.finance.modules.ledger.application.port.in.reference.CategoryRef;
import com.uit.finance.modules.ledger.application.port.in.reference.LedgerReferenceQuery;
import com.uit.finance.shared.kernel.UserId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Đọc danh mục qua {@code ledger::reference} và dịch sang {@link CategoryState} của budget. Chỗ duy
 * nhất của module biết hình dạng {@link CategoryRef}.
 */
final class BudgetCategories {

  private static final String EXPENSE = "EXPENSE";

  private final LedgerReferenceQuery ledger;

  BudgetCategories(LedgerReferenceQuery ledger) {
    this.ledger = ledger;
  }

  /** Rỗng khi danh mục chưa tới hoặc của user khác (ADR-006 B2). */
  Optional<CategoryState> state(UserId owner, UUID categoryId) {
    return ledger.findCategory(owner, categoryId).map(BudgetCategories::toState);
  }

  /** Một query cho cả lô; id không thấy không có trong kết quả. */
  Map<UUID, CategoryState> states(UserId owner, Set<UUID> categoryIds) {
    if (categoryIds.isEmpty()) {
      return Map.of();
    }
    return ledger.findCategories(owner, categoryIds).stream()
        .collect(Collectors.toMap(CategoryRef::id, BudgetCategories::toState));
  }

  private static CategoryState toState(CategoryRef ref) {
    return new CategoryState(EXPENSE.equals(ref.kind()), ref.parentId() == null, ref.deleted());
  }
}
