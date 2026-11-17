package com.uit.finance.modules.ledger.application.port.out.category;

import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.shared.kernel.UserId;

public interface SaveCategoryPort {

  /** Cùng hợp đồng với {@link SaveAccountPort#insertIfAbsent}. */
  boolean insertIfAbsent(UserId owner, Category category);

  /** {@code UPDATE ... WHERE id = :id AND user_id = :owner}. */
  void update(UserId owner, Category category);
}
