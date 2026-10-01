package com.uit.finance.modules.ledger.application.service;

import com.uit.finance.modules.ledger.application.port.in.TransactionView;
import com.uit.finance.modules.ledger.application.port.in.UpdateTransactionUseCase;
import com.uit.finance.modules.ledger.application.port.out.LoadTransactionPort;
import com.uit.finance.modules.ledger.application.port.out.SaveTransactionPort;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.modules.ledger.domain.model.LedgerEntity;
import com.uit.finance.modules.ledger.domain.model.Transaction;
import com.uit.finance.modules.ledger.domain.model.TransactionDetails;
import com.uit.finance.modules.ledger.domain.model.TransactionId;
import com.uit.finance.shared.kernel.DomainEventPublisher;
import com.uit.finance.shared.kernel.UserId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class UpdateTransactionService implements UpdateTransactionUseCase {

  private final LoadTransactionPort loadTransaction;
  private final SaveTransactionPort saveTransaction;
  private final TransactionReferenceResolver references;
  private final DomainEventPublisher events;
  private final Clock clock;

  UpdateTransactionService(
      LoadTransactionPort loadTransaction,
      SaveTransactionPort saveTransaction,
      TransactionReferenceResolver references,
      DomainEventPublisher events,
      Clock clock) {
    this.loadTransaction = loadTransaction;
    this.saveTransaction = saveTransaction;
    this.references = references;
    this.events = events;
    this.clock = clock;
  }

  @Override
  public TransactionView update(UpdateTransactionCommand command) {
    UserId owner = command.userId();
    TransactionId id = TransactionId.of(command.transactionId());
    Transaction transaction =
        loadTransaction
            .find(owner, id)
            .orElseThrow(() -> new LedgerNotFoundException(LedgerEntity.TRANSACTION, id.value()));

    TransactionDetails details = LedgerCommandMapper.toDetails(command.fields());
    boolean changed =
        transaction.revise(
            details,
            () -> references.resolve(owner, details),
            LedgerTime.today(clock),
            clock.instant());
    if (changed) {
      saveTransaction.update(owner, transaction);
      events.publishAll(transaction.pullDomainEvents());
    }
    return TransactionView.from(transaction);
  }
}
