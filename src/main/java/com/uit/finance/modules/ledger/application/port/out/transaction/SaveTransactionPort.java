package com.uit.finance.modules.ledger.application.port.out.transaction;

import com.uit.finance.modules.ledger.domain.model.transaction.Transaction;
import com.uit.finance.shared.kernel.UserId;

public interface SaveTransactionPort {

  /** Cùng hợp đồng với {@link SaveAccountPort#insertIfAbsent}. */
  boolean insertIfAbsent(UserId owner, Transaction transaction);

  /** {@code UPDATE ... WHERE id = :id AND user_id = :owner}. */
  void update(UserId owner, Transaction transaction);
}
