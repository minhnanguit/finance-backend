package com.uit.finance.modules.budget.adapter.in.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.finance.modules.budget.domain.exception.BudgetDeletedException;
import com.uit.finance.modules.budget.domain.exception.BudgetLimitReachedException;
import com.uit.finance.modules.budget.domain.exception.BudgetNotFoundException;
import com.uit.finance.modules.budget.domain.exception.BudgetReferencePendingException;
import com.uit.finance.modules.budget.domain.exception.DuplicateBudgetException;
import com.uit.finance.modules.budget.domain.exception.ImmutableBudgetFieldException;
import com.uit.finance.modules.budget.domain.exception.InvalidBudgetCategoryException;
import com.uit.finance.modules.budget.domain.exception.InvalidBudgetFieldException;
import com.uit.finance.shared.sync.SyncOpResult;
import com.uit.finance.shared.sync.SyncOutcome;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

/** Bảng dịch outcome của ADR-007 §5. */
class BudgetSyncVerdictsTest {

  private static final UUID ID = UUID.randomUUID();

  private static SyncOpResult thrown(RuntimeException e) {
    return BudgetSyncVerdicts.guard(
        () -> {
          throw e;
        });
  }

  @Test
  @DisplayName("danh mục chưa tới: RETRY; đã xoá: CONFLICT; đua optimistic lock: RETRY")
  void retryAndConflict() {
    assertThat(thrown(new BudgetReferencePendingException(ID)))
        .isEqualTo(SyncOpResult.retry(BudgetReferencePendingException.CODE));
    assertThat(thrown(new BudgetDeletedException(ID)))
        .isEqualTo(SyncOpResult.conflict(BudgetDeletedException.CODE));
    assertThat(thrown(new OptimisticLockingFailureException("race")))
        .isEqualTo(SyncOpResult.retry("budget.concurrent_update"));
  }

  @Test
  @DisplayName("mọi lỗi nghiệp vụ khác: REJECTED kèm đúng mã")
  void everythingElseIsRejected() {
    assertThat(thrown(new InvalidBudgetCategoryException(ID)).code())
        .isEqualTo("budget.invalid_category");
    assertThat(thrown(new DuplicateBudgetException(ID)).code()).isEqualTo("budget.duplicate");
    assertThat(thrown(new ImmutableBudgetFieldException("currency")).code())
        .isEqualTo("budget.immutable_field");
    assertThat(thrown(new BudgetLimitReachedException(100)).code())
        .isEqualTo("budget.limit_reached");
    assertThat(thrown(new InvalidBudgetFieldException("amountMinor")).code())
        .isEqualTo("budget.invalid_field");
    assertThat(thrown(new BudgetNotFoundException(ID)).code()).isEqualTo("budget.not_found");
    assertThat(thrown(new DuplicateBudgetException(ID)).outcome()).isEqualTo(SyncOutcome.REJECTED);
  }

  @Test
  @DisplayName("lỗi không phải nghiệp vụ bay lên để sync trả RETRY sync.server_error")
  void unexpectedErrorsPropagate() {
    IllegalStateException boom = new IllegalStateException("boom");
    assertThatThrownBy(() -> thrown(boom)).isSameAs(boom);
  }
}
