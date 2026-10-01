package com.uit.finance.modules.ledger.application.service;

import com.uit.finance.modules.ledger.application.port.in.RecordTransactionUseCase;
import com.uit.finance.modules.ledger.application.port.in.TransactionView;
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
class RecordTransactionService implements RecordTransactionUseCase {

  private final LoadTransactionPort loadTransaction;
  private final SaveTransactionPort saveTransaction;
  private final TransactionReferenceResolver references;
  private final DomainEventPublisher events;
  private final Clock clock;

  RecordTransactionService(
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
  public TransactionView record(RecordTransactionCommand command) {
    UserId owner = command.userId();
    TransactionId id = TransactionId.of(command.transactionId());
    TransactionDetails details = LedgerCommandMapper.toDetails(command.fields());

    Transaction transaction =
        CreateOnce.run(
            () -> loadTransaction.find(owner, id),
            () ->
                Transaction.record(
                    id,
                    owner,
                    details,
                    references.resolve(owner, details),
                    LedgerTime.today(clock),
                    clock.instant()),
            created -> saveTransaction.insertIfAbsent(owner, created),
            () -> new LedgerNotFoundException(LedgerEntity.TRANSACTION, id.value()));
    transaction.requireNotDeleted();
    // Bản đọc lại từ DB không mang event, nên gửi lại hay thua race đều không phát event lần hai.
    events.publishAll(transaction.pullDomainEvents());
    return TransactionView.from(transaction);
  }
}
