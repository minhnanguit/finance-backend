package com.uit.finance.modules.budget.domain.model;

import static com.uit.finance.modules.budget.domain.model.BudgetFixtures.ANN;
import static com.uit.finance.modules.budget.domain.model.BudgetFixtures.NOW;
import static com.uit.finance.modules.budget.domain.model.BudgetFixtures.USD;
import static com.uit.finance.modules.budget.domain.model.BudgetFixtures.VND;
import static com.uit.finance.modules.budget.domain.model.BudgetFixtures.assertRejected;
import static com.uit.finance.modules.budget.domain.model.BudgetFixtures.budget;
import static com.uit.finance.modules.budget.domain.model.BudgetFixtures.terms;
import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.modules.budget.domain.exception.BudgetDeletedException;
import com.uit.finance.modules.budget.domain.exception.ImmutableBudgetFieldException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BudgetTest {

  private final UUID food = UUID.randomUUID();

  @Test
  @DisplayName("chỉ số tiền sửa được; gửi lại y nguyên là no-op")
  void onlyAmountChanges() {
    Budget budget = budget(ANN, food, VND);

    assertThat(budget.revise(terms(food, VND, 3_000_000))).isFalse();
    assertThat(budget.revise(terms(food, VND, 5_000_000))).isTrue();
    assertThat(budget.getTerms().amount().minor()).isEqualTo(5_000_000);
  }

  @Test
  @DisplayName("danh mục, tiền tệ, tháng bắt đầu bất biến (G8)")
  void identityFieldsAreImmutable() {
    Budget budget = budget(ANN, food, VND);
    BudgetTerms current = budget.getTerms();

    assertRejected(
        () -> budget.revise(terms(UUID.randomUUID(), VND, 3_000_000)),
        ImmutableBudgetFieldException.CODE);
    assertRejected(
        () -> budget.revise(terms(food, USD, 3_000_000)), ImmutableBudgetFieldException.CODE);
    assertRejected(
        () ->
            budget.revise(
                new BudgetTerms(
                    food,
                    VND,
                    current.amount(),
                    new BudgetMonth(current.startMonth().firstDay().minusMonths(1)))),
        ImmutableBudgetFieldException.CODE);
    assertThat(budget.getTerms()).isEqualTo(current);
  }

  @Test
  @DisplayName("xoá lần hai là no-op; đã xoá thì không sửa (S4)")
  void deleteRules() {
    Budget budget = budget(ANN, food, VND);

    assertThat(budget.delete(NOW)).isTrue();
    assertThat(budget.delete(NOW.plusSeconds(60))).isFalse();
    assertThat(budget.getDeletedAt()).isEqualTo(NOW);
    assertRejected(() -> budget.revise(terms(food, VND, 1)), BudgetDeletedException.CODE);
  }

  @Test
  @DisplayName("không phát event nào")
  void recordsNoEvents() {
    Budget budget = budget(ANN, food, VND);
    budget.revise(terms(food, VND, 1));
    budget.delete(NOW);

    assertThat(budget.pullDomainEvents()).isEmpty();
  }
}
