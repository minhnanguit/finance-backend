package com.uit.finance.modules.ledger.application.service.transaction;

import com.uit.finance.modules.ledger.application.port.in.transaction.DeleteTransactionUseCase;
import com.uit.finance.modules.ledger.application.port.in.transaction.TransactionView;
import com.uit.finance.modules.ledger.application.port.out.transaction.LoadTransactionPort;
import com.uit.finance.modules.ledger.application.port.out.transaction.SaveTransactionPort;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.modules.ledger.domain.model.transaction.Transaction;
import com.uit.finance.modules.ledger.domain.model.transaction.TransactionId;
import com.uit.finance.shared.kernel.DomainEventPublisher;
import com.uit.finance.shared.kernel.UserId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class DeleteTransactionService implements DeleteTransactionUseCase {

  private final LoadTransactionPort loadTransaction;
  private final SaveTransactionPort saveTransaction;
  private final DomainEventPublisher events;
  private final Clock clock;

  DeleteTransactionService(
      LoadTransactionPort loadTransaction,
      SaveTransactionPort saveTransaction,
      DomainEventPublisher events,
      Clock clock) {
    this.loadTransaction = loadTransaction;
    this.saveTransaction = saveTransaction;
    this.events = events;
    this.clock = clock;
  }

  @Override
  public TransactionView delete(DeleteTransactionCommand command) {
    UserId owner = command.userId();
    TransactionId id = TransactionId.of(command.transactionId());
    Transaction transaction =
        loadTransaction
            .find(owner, id)
            .orElseThrow(() -> new LedgerNotFoundException(LedgerEntity.TRANSACTION, id.value()));

    if (transaction.delete(clock.instant())) {
      saveTransaction.update(owner, transaction);
      events.publishAll(transaction.pullDomainEvents());
    }
    return TransactionView.from(transaction);
  }
}
