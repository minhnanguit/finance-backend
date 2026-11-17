package com.uit.finance.modules.ledger.application.service.transaction;

import com.uit.finance.modules.ledger.application.port.in.transaction.TransactionChangeFeedUseCase;
import com.uit.finance.modules.ledger.application.port.in.transaction.TransactionView;
import com.uit.finance.modules.ledger.application.port.out.shared.Sequenced;
import com.uit.finance.modules.ledger.application.port.out.transaction.LoadTransactionChangesPort;
import com.uit.finance.modules.ledger.domain.model.transaction.Transaction;
import com.uit.finance.modules.ledger.domain.model.transaction.TransactionId;
import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class TransactionChangeFeedService implements TransactionChangeFeedUseCase {

  private final LoadTransactionChangesPort changes;

  TransactionChangeFeedService(LoadTransactionChangesPort changes) {
    this.changes = changes;
  }

  @Override
  public List<TransactionChange> since(UserId owner, long afterSeq, int limit) {
    return changes.changesSince(owner, afterSeq, limit).stream()
        .map(TransactionChangeFeedService::toChange)
        .toList();
  }

  @Override
  public Optional<TransactionChange> find(UserId owner, UUID transactionId) {
    return changes
        .findSequenced(owner, TransactionId.of(transactionId))
        .map(TransactionChangeFeedService::toChange);
  }

  private static TransactionChange toChange(Sequenced<Transaction> sequenced) {
    return new TransactionChange(TransactionView.from(sequenced.value()), sequenced.changeSeq());
  }
}
