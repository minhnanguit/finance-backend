package com.uit.finance.modules.ledger.application.port.out.transaction;

import com.uit.finance.modules.ledger.application.port.out.shared.Sequenced;
import com.uit.finance.modules.ledger.domain.model.transaction.Transaction;
import com.uit.finance.modules.ledger.domain.model.transaction.TransactionId;
import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.Optional;

/** Đọc cho sync: bản ghi kèm {@code change_seq}, kể cả tombstone. */
public interface LoadTransactionChangesPort {

  /** Tối đa {@code limit} bản ghi có {@code change_seq > afterSeq}, tăng dần. */
  List<Sequenced<Transaction>> changesSince(UserId owner, long afterSeq, int limit);

  /** Id của user khác thì rỗng. */
  Optional<Sequenced<Transaction>> findSequenced(UserId owner, TransactionId id);
}
