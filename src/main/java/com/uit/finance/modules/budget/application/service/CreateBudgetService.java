package com.uit.finance.modules.budget.application.service;

import com.uit.finance.modules.budget.application.port.in.BudgetFields;
import com.uit.finance.modules.budget.application.port.in.BudgetView;
import com.uit.finance.modules.budget.application.port.in.CreateBudgetUseCase;
import com.uit.finance.modules.budget.application.port.out.BudgetCountPort;
import com.uit.finance.modules.budget.application.port.out.BudgetWriteLockPort;
import com.uit.finance.modules.budget.application.port.out.LoadBudgetPort;
import com.uit.finance.modules.budget.application.port.out.SaveBudgetPort;
import com.uit.finance.modules.budget.domain.exception.BudgetLimitReachedException;
import com.uit.finance.modules.budget.domain.exception.BudgetNotFoundException;
import com.uit.finance.modules.budget.domain.exception.DuplicateBudgetException;
import com.uit.finance.modules.budget.domain.model.Budget;
import com.uit.finance.modules.budget.domain.model.BudgetId;
import com.uit.finance.modules.budget.domain.model.BudgetLimits;
import com.uit.finance.modules.budget.domain.model.BudgetTerms;
import com.uit.finance.modules.budget.domain.model.CategoryState;
import com.uit.finance.modules.ledger.application.port.in.reference.LedgerReferenceQuery;
import com.uit.finance.shared.kernel.UserId;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class CreateBudgetService implements CreateBudgetUseCase {

  private final BudgetWriteLockPort writeLock;
  private final LoadBudgetPort load;
  private final SaveBudgetPort save;
  private final BudgetCountPort count;
  private final BudgetCategories categories;
  private final StaleBudgets stale;
  private final Clock clock;

  CreateBudgetService(
      BudgetWriteLockPort writeLock,
      LoadBudgetPort load,
      SaveBudgetPort save,
      BudgetCountPort count,
      LedgerReferenceQuery ledger,
      Clock clock) {
    this.writeLock = writeLock;
    this.load = load;
    this.save = save;
    this.count = count;
    this.categories = new BudgetCategories(ledger);
    this.stale = new StaleBudgets(categories, save);
    this.clock = clock;
  }

  @Override
  public BudgetView create(CreateBudgetCommand command) {
    UserId owner = command.userId();
    writeLock.lock(owner);
    BudgetId id = BudgetId.of(command.budgetId());
    BudgetTerms terms = parse(command.fields());

    Optional<Budget> existing = load.find(owner, id);
    if (existing.isPresent()) {
      existing.get().requireNotDeleted();
      return BudgetView.from(existing.get());
    }

    CategoryState.requireUsable(terms.categoryId(), categories.state(owner, terms.categoryId()));
    if (load.findActive(owner, terms.categoryId(), terms.currency()).isPresent()) {
      throw new DuplicateBudgetException(terms.categoryId());
    }
    ensureCapacity(owner);

    Budget budget = Budget.create(id, owner, terms);
    // Bản của chính user đã được đọc dưới khoá ở trên: insert thua chỉ có thể là id của người khác.
    if (!save.insertIfAbsent(owner, budget)) {
      throw new BudgetNotFoundException(id.value());
    }
    return BudgetView.from(budget);
  }

  /**
   * Chạm giới hạn thì đối chiếu trước rồi mới từ chối: ngân sách của danh mục đã xoá mà consumer
   * chưa kịp dọn không được chiếm chỗ (ADR-007 GS8).
   */
  private void ensureCapacity(UserId owner) {
    if (count.countActive(owner) < BudgetLimits.MAX_BUDGETS_PER_USER) {
      return;
    }
    stale.purge(owner, load.findAllActive(owner), clock.instant());
    if (count.countActive(owner) >= BudgetLimits.MAX_BUDGETS_PER_USER) {
      throw new BudgetLimitReachedException(BudgetLimits.MAX_BUDGETS_PER_USER);
    }
  }

  private BudgetTerms parse(BudgetFields fields) {
    return BudgetTerms.parse(
        fields.categoryId(),
        fields.currency(),
        fields.amountMinor(),
        fields.startMonth(),
        BudgetTime.today(clock));
  }
}
