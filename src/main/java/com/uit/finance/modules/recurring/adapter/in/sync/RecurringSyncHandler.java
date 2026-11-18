package com.uit.finance.modules.recurring.adapter.in.sync;

import static com.uit.finance.modules.recurring.adapter.in.sync.RecurringSyncVerdicts.guard;

import com.uit.finance.modules.recurring.application.port.in.CreateRecurringUseCase;
import com.uit.finance.modules.recurring.application.port.in.CreateRecurringUseCase.CreateRecurringCommand;
import com.uit.finance.modules.recurring.application.port.in.DeleteRecurringUseCase;
import com.uit.finance.modules.recurring.application.port.in.DeleteRecurringUseCase.DeleteRecurringCommand;
import com.uit.finance.modules.recurring.application.port.in.RecurringChangeFeedUseCase;
import com.uit.finance.modules.recurring.application.port.in.RecurringChangeFeedUseCase.RecurringChange;
import com.uit.finance.modules.recurring.application.port.in.RecurringFields;
import com.uit.finance.modules.recurring.application.port.in.UpdateRecurringUseCase;
import com.uit.finance.modules.recurring.application.port.in.UpdateRecurringUseCase.UpdateRecurringCommand;
import com.uit.finance.modules.recurring.domain.exception.RecurringDeletedException;
import com.uit.finance.modules.recurring.domain.exception.RecurringNotFoundException;
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
 * Op của entity {@code recurring} (ADR-009 RS1): UPSERT = tạo nếu chưa có, sửa có điều kiện nếu đã
 * có; DELETE = tombstone. Mọi luật nằm ở use case. Module sync không biết recurring tồn tại.
 */
@Component
class RecurringSyncHandler implements SyncHandler {

  private final RecurringChangeFeedUseCase feed;
  private final CreateRecurringUseCase create;
  private final UpdateRecurringUseCase update;
  private final DeleteRecurringUseCase delete;

  RecurringSyncHandler(
      RecurringChangeFeedUseCase feed,
      CreateRecurringUseCase create,
      UpdateRecurringUseCase update,
      DeleteRecurringUseCase delete) {
    this.feed = feed;
    this.create = create;
    this.update = update;
    this.delete = delete;
  }

  @Override
  public String entity() {
    return RecurringSyncMapper.ENTITY;
  }

  @Override
  public SyncOpResult apply(UserId owner, SyncOp op) {
    return guard(
        () -> op.action() == SyncAction.DELETE ? delete(owner, op.id()) : upsert(owner, op));
  }

  /**
   * Id của user khác không thấy qua {@code feed}, nên đi vào nhánh tạo và nhận {@code
   * recurring.not_found} ở đó, không ghi đè (ADR-006 B1).
   */
  private SyncOpResult upsert(UserId owner, SyncOp op) {
    RecurringFields fields = RecurringSyncMapper.read(op.data());
    Optional<RecurringChange> existing = feed.find(owner, op.id());
    if (existing.isPresent() && existing.get().recurring().deleted()) {
      return SyncOpResult.conflict(RecurringDeletedException.CODE);
    }
    if (existing.isEmpty()) {
      create.create(new CreateRecurringCommand(owner, op.id(), fields));
    } else {
      update.update(new UpdateRecurringCommand(owner, op.id(), fields));
    }
    return SyncOpResult.applied();
  }

  private SyncOpResult delete(UserId owner, UUID id) {
    if (feed.find(owner, id).isEmpty()) {
      return SyncOpResult.rejected(RecurringNotFoundException.CODE);
    }
    delete.delete(new DeleteRecurringCommand(owner, id));
    return SyncOpResult.applied();
  }

  @Override
  public Optional<SyncChange> current(UserId owner, UUID id) {
    return feed.find(owner, id).map(RecurringSyncMapper::toChange);
  }

  @Override
  public List<SyncChange> changesSince(UserId owner, long afterSeq, int limit) {
    return feed.since(owner, afterSeq, limit).stream().map(RecurringSyncMapper::toChange).toList();
  }
}
