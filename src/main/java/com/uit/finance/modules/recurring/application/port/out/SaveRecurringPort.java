package com.uit.finance.modules.recurring.application.port.out;

import com.uit.finance.modules.recurring.domain.model.Recurring;
import com.uit.finance.shared.kernel.UserId;

public interface SaveRecurringPort {

  /**
   * Ghi khoản + timeline, {@code INSERT ... ON CONFLICT (id) DO NOTHING}, lấy {@code change_seq}
   * mới.
   *
   * @return {@code false} khi id đã có (của user khác, vì bản của chính user đã được đọc dưới khoá)
   */
  boolean insertIfAbsent(UserId owner, Recurring recurring);

  /**
   * Ghi đè phần cố định + timeline của bản đã có ({@code WHERE id = :id AND user_id = :owner}), lấy
   * {@code change_seq} mới.
   */
  void update(UserId owner, Recurring recurring);
}
