package com.uit.finance.modules.budget.application.port.out;

import com.uit.finance.modules.budget.domain.model.Budget;
import com.uit.finance.modules.budget.domain.model.BudgetId;
import com.uit.finance.shared.kernel.UserId;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoadBudgetPort {

  /** Trả cả bản đã xoá (tombstone). Id của user khác thì trả rỗng. */
  Optional<Budget> find(UserId owner, BudgetId id);

  /** Ngân sách chưa xoá của cặp (danh mục, tiền tệ); tối đa một bản (ADR-007 G3). */
  Optional<Budget> findActive(UserId owner, UUID categoryId, Currency currency);

  /** Mọi ngân sách chưa xoá của một danh mục, mọi tiền tệ (consumer GS6). */
  List<Budget> findActiveByCategory(UserId owner, UUID categoryId);

  /** Mọi ngân sách chưa xoá của user, tối đa vài trăm dòng (đối chiếu GS8). */
  List<Budget> findAllActive(UserId owner);
}
