package com.uit.finance.modules.budget.application.service;

import com.uit.finance.modules.budget.application.port.in.ReconcileBudgetsOfCategoryUseCase;
import com.uit.finance.modules.budget.application.port.out.BudgetWriteLockPort;
import com.uit.finance.modules.budget.application.port.out.LoadBudgetPort;
import com.uit.finance.modules.budget.application.port.out.SaveBudgetPort;
import com.uit.finance.modules.budget.domain.model.Budget;
import com.uit.finance.modules.ledger.application.port.in.reference.LedgerReferenceQuery;
import com.uit.finance.shared.kernel.UserId;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class ReconcileBudgetsOfCategoryService implements ReconcileBudgetsOfCategoryUseCase {

  private final BudgetWriteLockPort writeLock;
  private final LoadBudgetPort load;
  private final StaleBudgets stale;
  private final Clock clock;

  ReconcileBudgetsOfCategoryService(
      BudgetWriteLockPort writeLock,
      LoadBudgetPort load,
      SaveBudgetPort save,
      LedgerReferenceQuery ledger,
      Clock clock) {
    this.writeLock = writeLock;
    this.load = load;
    this.stale = new StaleBudgets(new BudgetCategories(ledger), save);
    this.clock = clock;
  }

  @Override
  public int reconcile(ReconcileBudgetsOfCategoryCommand command) {
    UserId owner = command.userId();
    writeLock.lock(owner);
    List<Budget> budgets = load.findActiveByCategory(owner, command.categoryId());
    if (budgets.isEmpty()) {
      return 0;
    }
    return stale.purgeOfCategory(owner, command.categoryId(), budgets, clock.instant());
  }
}
