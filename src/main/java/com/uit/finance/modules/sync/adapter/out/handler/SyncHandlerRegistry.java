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
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
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

  /**
   * Mỗi handler trả tối đa {@code limit} chủ sau {@code afterOwner}; trộn, bỏ trùng, xếp theo thứ
   * tự của Postgres rồi cắt lại: kết quả đúng là {@code limit} chủ đầu tiên của cả hệ thống.
   */
  @Override
  public List<UserId> ownersWithTombstonesBefore(
      Instant cutoff, @Nullable UserId afterOwner, int limit) {
    return handlers.values().stream()
        .flatMap(handler -> handler.ownersWithTombstonesBefore(cutoff, afterOwner, limit).stream())
        .distinct()
        .sorted(POSTGRES_UUID_ORDER)
        .limit(limit)
        .toList();
  }

  /**
   * Tombstone của entity này có thể còn bị tombstone của entity khác trỏ tới (ví ← giao dịch đã
   * xoá): handler chỉ xoá dòng không còn ai trỏ tới. Lặp tới khi một lượt không xoá thêm gì, nên
   * kết quả không phụ thuộc thứ tự duyệt handler (Map không giữ thứ tự) và cả chuỗi được dọn trong
   * một lần chạy. Chuỗi phụ thuộc dài nhất bằng số entity, nên tối đa {@code size + 1} lượt.
   */
  @Override
  public OptionalLong purgeTombstones(UserId owner, Instant cutoff) {
    OptionalLong highest = OptionalLong.empty();
    for (int pass = 0; pass <= handlers.size(); pass++) {
      OptionalLong purged =
          handlers.values().stream()
              .map(handler -> handler.purgeTombstones(owner, cutoff))
              .filter(OptionalLong::isPresent)
              .mapToLong(OptionalLong::getAsLong)
              .max();
      if (purged.isEmpty()) {
        break;
      }
      highest =
          highest.isPresent()
              ? OptionalLong.of(Math.max(highest.getAsLong(), purged.getAsLong()))
              : purged;
    }
    return highest;
  }

  /**
   * Postgres so sánh {@code uuid} theo từng byte không dấu; {@link java.util.UUID#compareTo} so
   * sánh hai nửa {@code long} có dấu nên lệch thứ tự với UUID có bit cao bật. Keyset phải dùng đúng
   * thứ tự của DB, nếu không lượt sau sẽ bỏ sót hoặc lặp user.
   */
  static final Comparator<UserId> POSTGRES_UUID_ORDER =
      Comparator.comparing(
          UserId::value,
          (a, b) -> {
            int high = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
            return high != 0
                ? high
                : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
          });

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
