package com.uit.finance.modules.budget.application.service;

import com.uit.finance.modules.budget.application.port.in.BudgetChangeFeedUseCase;
import com.uit.finance.modules.budget.application.port.in.BudgetView;
import com.uit.finance.modules.budget.application.port.out.LoadBudgetChangesPort;
import com.uit.finance.modules.budget.application.port.out.Sequenced;
import com.uit.finance.modules.budget.domain.model.Budget;
import com.uit.finance.modules.budget.domain.model.BudgetId;
import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class BudgetChangeFeedService implements BudgetChangeFeedUseCase {

  private final LoadBudgetChangesPort changes;

  BudgetChangeFeedService(LoadBudgetChangesPort changes) {
    this.changes = changes;
  }

  @Override
  public List<BudgetChange> since(UserId owner, long afterSeq, int limit) {
    return changes.changesSince(owner, afterSeq, limit).stream()
        .map(BudgetChangeFeedService::toChange)
        .toList();
  }

  @Override
  public Optional<BudgetChange> find(UserId owner, UUID budgetId) {
    return changes
        .findSequenced(owner, BudgetId.of(budgetId))
        .map(BudgetChangeFeedService::toChange);
  }

  private static BudgetChange toChange(Sequenced<Budget> sequenced) {
    return new BudgetChange(BudgetView.from(sequenced.value()), sequenced.changeSeq());
  }
}
