package com.uit.finance.modules.ledger.adapter.in.sync.category;

import static com.uit.finance.modules.ledger.adapter.in.sync.support.LedgerSyncVerdicts.guard;

import com.uit.finance.modules.ledger.application.port.in.category.ArchiveCategoryUseCase;
import com.uit.finance.modules.ledger.application.port.in.category.ArchiveCategoryUseCase.ArchiveCategoryCommand;
import com.uit.finance.modules.ledger.application.port.in.category.CategoryChangeFeedUseCase;
import com.uit.finance.modules.ledger.application.port.in.category.CategoryChangeFeedUseCase.CategoryChange;
import com.uit.finance.modules.ledger.application.port.in.category.CreateCategoryUseCase;
import com.uit.finance.modules.ledger.application.port.in.category.CreateCategoryUseCase.CreateCategoryCommand;
import com.uit.finance.modules.ledger.application.port.in.category.DeleteCategoryUseCase;
import com.uit.finance.modules.ledger.application.port.in.category.DeleteCategoryUseCase.DeleteCategoryCommand;
import com.uit.finance.modules.ledger.application.port.in.category.SeedDefaultCategoriesUseCase;
import com.uit.finance.modules.ledger.application.port.in.category.UpdateCategoryUseCase;
import com.uit.finance.modules.ledger.application.port.in.category.UpdateCategoryUseCase.UpdateCategoryCommand;
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

/** Op của entity {@code category}. Lần sync đầu tiên tạo bộ danh mục mặc định (ADR-002 §5). */
@Component
class CategorySyncHandler implements SyncHandler {

  private final CategoryChangeFeedUseCase feed;
  private final CreateCategoryUseCase create;
  private final UpdateCategoryUseCase update;
  private final ArchiveCategoryUseCase archive;
  private final DeleteCategoryUseCase delete;
  private final SeedDefaultCategoriesUseCase seed;

  CategorySyncHandler(
      CategoryChangeFeedUseCase feed,
      CreateCategoryUseCase create,
      UpdateCategoryUseCase update,
      ArchiveCategoryUseCase archive,
      DeleteCategoryUseCase delete,
      SeedDefaultCategoriesUseCase seed) {
    this.feed = feed;
    this.create = create;
    this.update = update;
    this.archive = archive;
    this.delete = delete;
    this.seed = seed;
  }

  @Override
  public String entity() {
    return CategorySyncMapper.ENTITY;
  }

  @Override
  public SyncOpResult apply(UserId owner, SyncOp op) {
    return guard(
        () -> op.action() == SyncAction.DELETE ? delete(owner, op.id()) : upsert(owner, op));
  }

  private SyncOpResult upsert(UserId owner, SyncOp op) {
    CategorySyncMapper.Input input = CategorySyncMapper.read(op.data());
    Optional<CategoryChange> existing = feed.find(owner, op.id());
    if (existing.isPresent() && existing.get().category().deleted()) {
      return SyncOpResult.conflict(EntityDeletedException.CODE);
    }
    if (existing.isEmpty()) {
      create.create(new CreateCategoryCommand(owner, op.id(), input.fields()));
    } else {
      update.update(new UpdateCategoryCommand(owner, op.id(), input.fields()));
    }
    archive.setArchived(new ArchiveCategoryCommand(owner, op.id(), input.archived()));
    return SyncOpResult.applied();
  }

  private SyncOpResult delete(UserId owner, UUID id) {
    if (feed.find(owner, id).isEmpty()) {
      return SyncOpResult.rejected(LedgerNotFoundException.CODE);
    }
    delete.delete(new DeleteCategoryCommand(owner, id));
    return SyncOpResult.applied();
  }

  @Override
  public Optional<SyncChange> current(UserId owner, UUID id) {
    return feed.find(owner, id).map(CategorySyncMapper::toChange);
  }

  @Override
  public List<SyncChange> changesSince(UserId owner, long afterSeq, int limit) {
    return feed.since(owner, afterSeq, limit).stream().map(CategorySyncMapper::toChange).toList();
  }

  @Override
  public void initialize(UserId owner) {
    seed.seed(owner);
  }
}
