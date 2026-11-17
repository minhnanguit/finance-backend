package com.uit.finance.modules.budget.application.port.in;

import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;

/** Xoá mềm ngân sách (tombstone). Xoá lại là no-op; không có hoặc của user khác: not_found. */
public interface DeleteBudgetUseCase {

  BudgetView delete(DeleteBudgetCommand command);

  record DeleteBudgetCommand(UserId userId, UUID budgetId) {}
}
