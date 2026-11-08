package com.uit.finance.modules.budget.application.port.out;

import com.uit.finance.modules.budget.domain.model.Budget;
import com.uit.finance.shared.kernel.UserId;

public interface SaveBudgetPort {

  /**
   * {@code INSERT ... ON CONFLICT (id) DO NOTHING}, lấy {@code change_seq} mới.
   *
   * @return {@code false} khi id đã có (của user khác, vì bản của chính user đã được đọc dưới khoá)
   * @throws com.uit.finance.modules.budget.domain.exception.DuplicateBudgetException khi đụng
   *     unique index (danh mục, tiền tệ)
   */
  boolean insertIfAbsent(UserId owner, Budget budget);

  /** {@code UPDATE ... WHERE id = :id AND user_id = :owner}, lấy {@code change_seq} mới. */
  void update(UserId owner, Budget budget);
}
