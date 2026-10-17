package com.uit.finance.modules.budget.domain.model;

import static com.uit.finance.modules.budget.domain.model.BudgetFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.uit.finance.modules.budget.domain.exception.BudgetReferencePendingException;
import com.uit.finance.modules.budget.domain.exception.InvalidBudgetCategoryException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CategoryStateTest {

  private static final UUID ID = UUID.randomUUID();

  @Test
  @DisplayName("chỉ danh mục chi, cấp 1, chưa xoá giữ được ngân sách (G2)")
  void onlyLiveTopLevelExpense() {
    assertThatCode(
            () ->
                CategoryState.requireUsable(ID, Optional.of(new CategoryState(true, true, false))))
        .doesNotThrowAnyException();
    assertRejected(
        () -> CategoryState.requireUsable(ID, Optional.of(new CategoryState(false, true, false))),
        InvalidBudgetCategoryException.CODE);
    assertRejected(
        () -> CategoryState.requireUsable(ID, Optional.of(new CategoryState(true, false, false))),
        InvalidBudgetCategoryException.CODE);
    assertRejected(
        () -> CategoryState.requireUsable(ID, Optional.of(new CategoryState(true, true, true))),
        InvalidBudgetCategoryException.CODE);
  }

  @Test
  @DisplayName("không thấy danh mục là reference_pending (B2)")
  void missingIsPending() {
    assertRejected(
        () -> CategoryState.requireUsable(ID, Optional.empty()),
        BudgetReferencePendingException.CODE);
  }
}
