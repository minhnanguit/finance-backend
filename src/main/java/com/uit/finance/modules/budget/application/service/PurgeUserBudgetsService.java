package com.uit.finance.modules.budget.application.service;

import com.uit.finance.modules.budget.application.port.in.PurgeUserBudgetsUseCase;
import com.uit.finance.modules.budget.application.port.out.PurgeBudgetsPort;
import com.uit.finance.shared.kernel.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Một câu DELETE trong một transaction. Không cần khoá ghi của user: event chỉ được phát khi không
 * còn token nào của user ghi được (ADR-008 A7).
 */
@Service
@Transactional
class PurgeUserBudgetsService implements PurgeUserBudgetsUseCase {

  private final PurgeBudgetsPort budgets;

  PurgeUserBudgetsService(PurgeBudgetsPort budgets) {
    this.budgets = budgets;
  }

  @Override
  public long purge(UserId owner) {
    return budgets.deleteEverythingOf(owner);
  }
}
