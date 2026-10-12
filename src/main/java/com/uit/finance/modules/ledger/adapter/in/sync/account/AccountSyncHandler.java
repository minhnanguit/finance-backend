package com.uit.finance.modules.ledger.adapter.in.sync.account;

import static com.uit.finance.modules.ledger.adapter.in.sync.support.LedgerSyncVerdicts.guard;

import com.uit.finance.modules.ledger.application.port.in.account.AccountChangeFeedUseCase;
import com.uit.finance.modules.ledger.application.port.in.account.AccountChangeFeedUseCase.AccountChange;
import com.uit.finance.modules.ledger.application.port.in.account.ArchiveAccountUseCase;
import com.uit.finance.modules.ledger.application.port.in.account.ArchiveAccountUseCase.ArchiveAccountCommand;
import com.uit.finance.modules.ledger.application.port.in.account.CreateAccountUseCase;
import com.uit.finance.modules.ledger.application.port.in.account.CreateAccountUseCase.CreateAccountCommand;
import com.uit.finance.modules.ledger.application.port.in.account.DeleteAccountUseCase;
import com.uit.finance.modules.ledger.application.port.in.account.DeleteAccountUseCase.DeleteAccountCommand;
import com.uit.finance.modules.ledger.application.port.in.account.UpdateAccountUseCase;
import com.uit.finance.modules.ledger.application.port.in.account.UpdateAccountUseCase.UpdateAccountCommand;
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
 * Op của entity {@code account}: UPSERT = tạo nếu chưa có, sửa nếu đã có (kể cả cờ archive); DELETE
 * = tombstone. Chỉ gọi use case của ledger, mọi luật nằm ở đó.
 */
@Component
class AccountSyncHandler implements SyncHandler {

  private final AccountChangeFeedUseCase feed;
  private final CreateAccountUseCase create;
  private final UpdateAccountUseCase update;
  private final ArchiveAccountUseCase archive;
  private final DeleteAccountUseCase delete;

  AccountSyncHandler(
      AccountChangeFeedUseCase feed,
      CreateAccountUseCase create,
      UpdateAccountUseCase update,
      ArchiveAccountUseCase archive,
      DeleteAccountUseCase delete) {
    this.feed = feed;
    this.create = create;
    this.update = update;
    this.archive = archive;
    this.delete = delete;
  }

  @Override
  public String entity() {
    return AccountSyncMapper.ENTITY;
  }

  @Override
  public SyncOpResult apply(UserId owner, SyncOp op) {
    return guard(
        () -> op.action() == SyncAction.DELETE ? delete(owner, op.id()) : upsert(owner, op));
  }

  private SyncOpResult upsert(UserId owner, SyncOp op) {
    AccountSyncMapper.Input input = AccountSyncMapper.read(op.data());
    Optional<AccountChange> existing = feed.find(owner, op.id());
    if (existing.isPresent() && existing.get().account().deleted()) {
      return SyncOpResult.conflict(EntityDeletedException.CODE);
    }
    if (existing.isEmpty()) {
      create.create(new CreateAccountCommand(owner, op.id(), input.fields()));
    } else {
      update.update(new UpdateAccountCommand(owner, op.id(), input.fields()));
    }
    archive.setArchived(new ArchiveAccountCommand(owner, op.id(), input.archived()));
    return SyncOpResult.applied();
  }

  private SyncOpResult delete(UserId owner, UUID id) {
    if (feed.find(owner, id).isEmpty()) {
      return SyncOpResult.rejected(LedgerNotFoundException.CODE);
    }
    delete.delete(new DeleteAccountCommand(owner, id));
    return SyncOpResult.applied();
  }

  @Override
  public Optional<SyncChange> current(UserId owner, UUID id) {
    return feed.find(owner, id).map(AccountSyncMapper::toChange);
  }

  @Override
  public List<SyncChange> changesSince(UserId owner, long afterSeq, int limit) {
    return feed.since(owner, afterSeq, limit).stream().map(AccountSyncMapper::toChange).toList();
  }
}
