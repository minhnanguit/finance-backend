package com.uit.finance.modules.ledger.application.port.out;

import com.uit.finance.modules.ledger.domain.model.Account;
import com.uit.finance.shared.kernel.UserId;

public interface SaveAccountPort {

  /**
   * Trả {@code false} thay vì ném exception khi id đã có, dù của ai: exception đi qua proxy
   * {@code @Transactional} sẽ đánh dấu cả transaction là rollback-only.
   *
   * @return {@code true} nếu lần gọi này insert được dòng
   */
  boolean insertIfAbsent(UserId owner, Account account);

  /**
   * {@code UPDATE ... WHERE id = :id AND user_id = :owner}. Không bao giờ đụng dòng của user khác.
   */
  void update(UserId owner, Account account);
}
