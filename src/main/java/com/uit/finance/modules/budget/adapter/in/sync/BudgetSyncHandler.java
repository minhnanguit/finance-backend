package com.uit.finance.modules.budget.adapter.in.sync;

import static com.uit.finance.modules.budget.adapter.in.sync.BudgetSyncVerdicts.guard;

import com.uit.finance.modules.budget.application.port.in.BudgetChangeFeedUseCase;
import com.uit.finance.modules.budget.application.port.in.BudgetChangeFeedUseCase.BudgetChange;
import com.uit.finance.modules.budget.application.port.in.BudgetFields;
import com.uit.finance.modules.budget.application.port.in.CreateBudgetUseCase;
import com.uit.finance.modules.budget.application.port.in.CreateBudgetUseCase.CreateBudgetCommand;
import com.uit.finance.modules.budget.application.port.in.DeleteBudgetUseCase;
import com.uit.finance.modules.budget.application.port.in.DeleteBudgetUseCase.DeleteBudgetCommand;
import com.uit.finance.modules.budget.application.port.in.UpdateBudgetUseCase;
import com.uit.finance.modules.budget.application.port.in.UpdateBudgetUseCase.UpdateBudgetCommand;
import com.uit.finance.modules.budget.domain.exception.BudgetDeletedException;
import com.uit.finance.modules.budget.domain.exception.BudgetNotFoundException;
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
 * Op của entity {@code budget} (ADR-007 GS1): UPSERT = tạo nếu chưa có, sửa số tiền nếu đã có;
 * DELETE = tombstone. Chỉ gọi use case của budget, mọi luật nằm ở đó. Module sync không biết budget
 * tồn tại: Spring tự tìm bean này (ADR-002 §6).
 */
@Component
class BudgetSyncHandler implements SyncHandler {

  private final BudgetChangeFeedUseCase feed;
  private final CreateBudgetUseCase create;
  private final UpdateBudgetUseCase update;
  private final DeleteBudgetUseCase delete;

  BudgetSyncHandler(
      BudgetChangeFeedUseCase feed,
      CreateBudgetUseCase create,
      UpdateBudgetUseCase update,
      DeleteBudgetUseCase delete) {
    this.feed = feed;
    this.create = create;
    this.update = update;
    this.delete = delete;
  }

  @Override
  public String entity() {
    return BudgetSyncMapper.ENTITY;
  }

  @Override
  public SyncOpResult apply(UserId owner, SyncOp op) {
    return guard(
        () -> op.action() == SyncAction.DELETE ? delete(owner, op.id()) : upsert(owner, op));
  }

  /**
   * Id của user khác không thấy qua {@code feed}, nên đi vào nhánh tạo và nhận {@code
   * budget.not_found} ở đó, không ghi đè (ADR-006 B1).
   */
  private SyncOpResult upsert(UserId owner, SyncOp op) {
    BudgetFields fields = BudgetSyncMapper.read(op.data());
    Optional<BudgetChange> existing = feed.find(owner, op.id());
    if (existing.isPresent() && existing.get().budget().deleted()) {
      return SyncOpResult.conflict(BudgetDeletedException.CODE);
    }
    if (existing.isEmpty()) {
      create.create(new CreateBudgetCommand(owner, op.id(), fields));
    } else {
      update.update(new UpdateBudgetCommand(owner, op.id(), fields));
    }
    return SyncOpResult.applied();
  }

  private SyncOpResult delete(UserId owner, UUID id) {
    if (feed.find(owner, id).isEmpty()) {
      return SyncOpResult.rejected(BudgetNotFoundException.CODE);
    }
    delete.delete(new DeleteBudgetCommand(owner, id));
    return SyncOpResult.applied();
  }

  @Override
  public Optional<SyncChange> current(UserId owner, UUID id) {
    return feed.find(owner, id).map(BudgetSyncMapper::toChange);
  }

  @Override
  public List<SyncChange> changesSince(UserId owner, long afterSeq, int limit) {
    return feed.since(owner, afterSeq, limit).stream().map(BudgetSyncMapper::toChange).toList();
  }
}
