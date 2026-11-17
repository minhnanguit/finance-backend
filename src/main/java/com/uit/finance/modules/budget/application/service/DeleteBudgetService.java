package com.uit.finance.modules.budget.application.service;

import com.uit.finance.modules.budget.application.port.in.BudgetView;
import com.uit.finance.modules.budget.application.port.in.DeleteBudgetUseCase;
import com.uit.finance.modules.budget.application.port.out.BudgetWriteLockPort;
import com.uit.finance.modules.budget.application.port.out.LoadBudgetPort;
import com.uit.finance.modules.budget.application.port.out.SaveBudgetPort;
import com.uit.finance.modules.budget.domain.exception.BudgetNotFoundException;
import com.uit.finance.modules.budget.domain.model.Budget;
import com.uit.finance.modules.budget.domain.model.BudgetId;
import com.uit.finance.shared.kernel.UserId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class DeleteBudgetService implements DeleteBudgetUseCase {

  private final BudgetWriteLockPort writeLock;
  private final LoadBudgetPort load;
  private final SaveBudgetPort save;
  private final Clock clock;

  DeleteBudgetService(
      BudgetWriteLockPort writeLock, LoadBudgetPort load, SaveBudgetPort save, Clock clock) {
    this.writeLock = writeLock;
    this.load = load;
    this.save = save;
    this.clock = clock;
  }

  @Override
  public BudgetView delete(DeleteBudgetCommand command) {
    UserId owner = command.userId();
    writeLock.lock(owner);
    BudgetId id = BudgetId.of(command.budgetId());
    Budget budget = load.find(owner, id).orElseThrow(() -> new BudgetNotFoundException(id.value()));

    if (budget.delete(clock.instant())) {
      save.update(owner, budget);
    }
    return BudgetView.from(budget);
  }
}
