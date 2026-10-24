package com.uit.finance.modules.budget.application.port.in;

import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;

/**
 * Tạo ngân sách theo id do client sinh (ADR-007 §3, §5).
 *
 * <ul>
 *   <li>Id đã là của chính user: gửi lại, trả bản đang có, không ghi. Bản đó đã xoá: {@code
 *       budget.deleted}.
 *   <li>Id của user khác: {@code budget.not_found}, không ghi đè (ADR-006 B1).
 *   <li>Danh mục chưa thấy: {@code budget.reference_pending}; không hợp lệ: {@code
 *       budget.invalid_category}; cặp (danh mục, tiền tệ) đã có: {@code budget.duplicate}; quá giới
 *       hạn: {@code budget.limit_reached}.
 * </ul>
 */
public interface CreateBudgetUseCase {

  BudgetView create(CreateBudgetCommand command);

  record CreateBudgetCommand(UserId userId, UUID budgetId, BudgetFields fields) {}
}
