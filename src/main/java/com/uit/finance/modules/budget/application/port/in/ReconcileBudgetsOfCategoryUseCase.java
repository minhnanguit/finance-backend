package com.uit.finance.modules.budget.application.port.in;

import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;

/**
 * Đọc lại trạng thái hiện tại của danh mục rồi xoá mềm mọi ngân sách của nó nếu danh mục đã xoá
 * hoặc không còn là cấp 1 (ADR-007 GS6). Không tin nội dung event: gọi bao nhiêu lần, theo thứ tự
 * nào cũng ra cùng kết quả. Danh mục không thấy thì không làm gì.
 */
public interface ReconcileBudgetsOfCategoryUseCase {

  /** Số ngân sách vừa bị xoá. */
  int reconcile(ReconcileBudgetsOfCategoryCommand command);

  record ReconcileBudgetsOfCategoryCommand(UserId userId, UUID categoryId) {}
}
