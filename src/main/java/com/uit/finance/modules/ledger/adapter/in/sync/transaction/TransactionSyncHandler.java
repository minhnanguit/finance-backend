package com.uit.finance.modules.ledger.adapter.in.sync.transaction;

import static com.uit.finance.modules.ledger.adapter.in.sync.support.LedgerSyncVerdicts.guard;

import com.uit.finance.modules.ledger.application.port.in.transaction.DeleteTransactionUseCase;
import com.uit.finance.modules.ledger.application.port.in.transaction.DeleteTransactionUseCase.DeleteTransactionCommand;
import com.uit.finance.modules.ledger.application.port.in.transaction.RecordTransactionUseCase;
import com.uit.finance.modules.ledger.application.port.in.transaction.RecordTransactionUseCase.RecordTransactionCommand;
import com.uit.finance.modules.ledger.application.port.in.transaction.TransactionChangeFeedUseCase;
import com.uit.finance.modules.ledger.application.port.in.transaction.TransactionChangeFeedUseCase.TransactionChange;
import com.uit.finance.modules.ledger.application.port.in.transaction.TransactionFields;
import com.uit.finance.modules.ledger.application.port.in.transaction.UpdateTransactionUseCase;
import com.uit.finance.modules.ledger.application.port.in.transaction.UpdateTransactionUseCase.UpdateTransactionCommand;
import com.uit.finance.modules.ledger.domain.exception.EntityDeletedException;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.shared.kernel.UserId;
import com.uit.finance.shared.sync.SyncAction;
import com.uit.finance.shared.sync.SyncChange;
import com.uit.finance.shared.sync.SyncHandler;
import com.uit.finance.shared.sync.SyncOp;
import com.uit.finance.shared.sync.SyncOpResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Op của entity {@code transaction}. Ví/danh mục được trỏ tới chưa thấy → {@code RETRY}, giống hệt
 * khi chúng là của user khác (ADR-006 B2).
 */
@Component
class TransactionSyncHandler implements SyncHandler {

  private final TransactionChangeFeedUseCase feed;
  private final RecordTransactionUseCase record;
  private final UpdateTransactionUseCase update;
  private final DeleteTransactionUseCase delete;

  TransactionSyncHandler(
      TransactionChangeFeedUseCase feed,
      RecordTransactionUseCase record,
      UpdateTransactionUseCase update,
      DeleteTransactionUseCase delete) {
    this.feed = feed;
    this.record = record;
    this.update = update;
    this.delete = delete;
  }

  @Override
  public String entity() {
    return TransactionSyncMapper.ENTITY;
  }

  @Override
  public SyncOpResult apply(UserId owner, SyncOp op) {
    return guard(
        () -> op.action() == SyncAction.DELETE ? delete(owner, op.id()) : upsert(owner, op));
  }

  private SyncOpResult upsert(UserId owner, SyncOp op) {
    TransactionFields fields = TransactionSyncMapper.read(op.data());
    Optional<TransactionChange> existing = feed.find(owner, op.id());
    if (existing.isPresent() && existing.get().transaction().deleted()) {
      return SyncOpResult.conflict(EntityDeletedException.CODE);
    }
    if (existing.isEmpty()) {
      record.record(new RecordTransactionCommand(owner, op.id(), fields));
    } else {
      update.update(new UpdateTransactionCommand(owner, op.id(), fields));
    }
    return SyncOpResult.applied();
  }

  private SyncOpResult delete(UserId owner, UUID id) {
    if (feed.find(owner, id).isEmpty()) {
      return SyncOpResult.rejected(LedgerNotFoundException.CODE);
    }
    delete.delete(new DeleteTransactionCommand(owner, id));
    return SyncOpResult.applied();
  }

  @Override
  public Optional<SyncChange> current(UserId owner, UUID id) {
    return feed.find(owner, id).map(TransactionSyncMapper::toChange);
  }

  @Override
  public List<SyncChange> changesSince(UserId owner, long afterSeq, int limit) {
    return feed.since(owner, afterSeq, limit).stream()
        .map(TransactionSyncMapper::toChange)
        .toList();
  }
}
