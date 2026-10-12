package com.uit.finance.modules.sync.adapter.out.handler;

import com.uit.finance.modules.sync.application.port.out.SyncHandlersPort;
import com.uit.finance.modules.sync.domain.model.ChangeRecord;
import com.uit.finance.modules.sync.domain.model.OpOutcome;
import com.uit.finance.modules.sync.domain.model.OpVerdict;
import com.uit.finance.modules.sync.domain.model.PushedOp;
import com.uit.finance.shared.kernel.UserId;
import com.uit.finance.shared.sync.SyncAction;
import com.uit.finance.shared.sync.SyncChange;
import com.uit.finance.shared.sync.SyncHandler;
import com.uit.finance.shared.sync.SyncOp;
import com.uit.finance.shared.sync.SyncOpResult;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Mọi {@link SyncHandler} Spring tìm thấy, theo tên entity. Module mới chỉ cần thêm bean handler,
 * không sửa gì ở đây (Open/Closed). Hai handler trùng tên thì app không khởi động.
 */
@Component
class SyncHandlerRegistry implements SyncHandlersPort {

  private final Map<String, SyncHandler> handlers;

  SyncHandlerRegistry(List<SyncHandler> handlers) {
    this.handlers =
        handlers.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    SyncHandler::entity,
                    Function.identity(),
                    (first, second) -> {
                      throw new IllegalStateException(
                          "Two sync handlers for entity '" + first.entity() + "'");
                    }));
  }

  @Override
  public boolean supports(String entity) {
    return handlers.containsKey(entity);
  }

  @Override
  public OpVerdict apply(UserId owner, PushedOp op) {
    SyncOpResult result =
        handler(op.entity())
            .apply(
                owner,
                new SyncOp(
                    op.opId(),
                    op.entity(),
                    op.entityId(),
                    SyncAction.valueOf(op.action().name()),
                    op.data()));
    return new OpVerdict(OpOutcome.valueOf(result.outcome().name()), result.code());
  }

  @Override
  public Optional<ChangeRecord> current(UserId owner, String entity, UUID id) {
    SyncHandler handler = handlers.get(entity);
    return handler == null
        ? Optional.empty()
        : handler.current(owner, id).map(SyncHandlerRegistry::toRecord);
  }

  /**
   * Mỗi entity trả tối đa {@code limit}, trộn theo {@code changeSeq} rồi cắt lại còn {@code limit}.
   */
  @Override
  public List<ChangeRecord> changesSince(UserId owner, long afterSeq, int limit) {
    return handlers.values().stream()
        .flatMap(handler -> handler.changesSince(owner, afterSeq, limit).stream())
        .sorted(Comparator.comparingLong(SyncChange::changeSeq))
        .limit(limit)
        .map(SyncHandlerRegistry::toRecord)
        .toList();
  }

  @Override
  public void initialize(UserId owner) {
    handlers.values().forEach(handler -> handler.initialize(owner));
  }

  private SyncHandler handler(String entity) {
    SyncHandler handler = handlers.get(entity);
    if (handler == null) {
      throw new IllegalArgumentException("No sync handler for entity " + entity);
    }
    return handler;
  }

  private static ChangeRecord toRecord(SyncChange change) {
    return new ChangeRecord(
        change.entity(), change.id(), change.changeSeq(), change.deleted(), change.data());
  }
}
