package com.uit.finance.modules.budget.application.port.in;

import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;

/**
 * Sửa số tiền của ngân sách (ADR-007 G5, G8). Đổi field bất biến: {@code budget.immutable_field}.
 * Đã xoá: {@code budget.deleted}. Không có hoặc của user khác: {@code budget.not_found}.
 */
public interface UpdateBudgetUseCase {

  BudgetView update(UpdateBudgetCommand command);

  record UpdateBudgetCommand(UserId userId, UUID budgetId, BudgetFields fields) {}
}
