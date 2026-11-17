package com.uit.finance.modules.budget.application.service;

import com.uit.finance.modules.budget.application.port.in.BudgetFields;
import com.uit.finance.modules.budget.application.port.in.BudgetView;
import com.uit.finance.modules.budget.application.port.in.UpdateBudgetUseCase;
import com.uit.finance.modules.budget.application.port.out.BudgetWriteLockPort;
import com.uit.finance.modules.budget.application.port.out.LoadBudgetPort;
import com.uit.finance.modules.budget.application.port.out.SaveBudgetPort;
import com.uit.finance.modules.budget.domain.exception.BudgetNotFoundException;
import com.uit.finance.modules.budget.domain.model.Budget;
import com.uit.finance.modules.budget.domain.model.BudgetId;
import com.uit.finance.modules.budget.domain.model.BudgetTerms;
import com.uit.finance.shared.kernel.UserId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class UpdateBudgetService implements UpdateBudgetUseCase {

  private final BudgetWriteLockPort writeLock;
  private final LoadBudgetPort load;
  private final SaveBudgetPort save;
  private final Clock clock;

  UpdateBudgetService(
      BudgetWriteLockPort writeLock, LoadBudgetPort load, SaveBudgetPort save, Clock clock) {
    this.writeLock = writeLock;
    this.load = load;
    this.save = save;
    this.clock = clock;
  }

  @Override
  public BudgetView update(UpdateBudgetCommand command) {
    UserId owner = command.userId();
    writeLock.lock(owner);
    BudgetId id = BudgetId.of(command.budgetId());
    Budget budget = load.find(owner, id).orElseThrow(() -> new BudgetNotFoundException(id.value()));
    // Xoá luôn thắng (S4): bản đã xoá trả CONFLICT trước khi xét tới dữ liệu gửi lên.
    budget.requireNotDeleted();

    if (budget.revise(parse(command.fields()))) {
      save.update(owner, budget);
    }
    return BudgetView.from(budget);
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
