package com.uit.finance.modules.ledger.application.port.out.transaction;

import com.uit.finance.modules.ledger.domain.model.transaction.Transaction;
import com.uit.finance.modules.ledger.domain.model.transaction.TransactionId;
import com.uit.finance.shared.kernel.UserId;
import java.util.Optional;

public interface LoadTransactionPort {

  /** Trả cả bản đã xoá (tombstone). Id của user khác thì trả rỗng. */
  Optional<Transaction> find(UserId owner, TransactionId id);
}
