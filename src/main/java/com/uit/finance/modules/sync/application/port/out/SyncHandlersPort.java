package com.uit.finance.modules.sync.application.port.out;

import com.uit.finance.modules.sync.domain.model.ChangeRecord;
import com.uit.finance.modules.sync.domain.model.OpVerdict;
import com.uit.finance.modules.sync.domain.model.PushedOp;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Các module có dữ liệu đồng bộ, nhìn từ phía sync. Adapter chuyển tới {@code SyncHandler} trong
 * {@code shared.sync}; application không đụng SPI đó trực tiếp (ArchUnit luật #7).
 */
public interface SyncHandlersPort {

  boolean supports(String entity);

  OpVerdict apply(UserId owner, PushedOp op);

  /** Rỗng khi entity không có, bản ghi không có, hoặc là của user khác. */
  Optional<ChangeRecord> current(UserId owner, String entity, UUID id);

  /** Thay đổi của mọi entity có {@code changeSeq > afterSeq}, tăng dần, tối đa {@code limit}. */
  List<ChangeRecord> changesSince(UserId owner, long afterSeq, int limit);

  void initialize(UserId owner);

  /**
   * Chủ có tombstone bị xoá trước {@code cutoff} ở bất kỳ entity nào, id lớn hơn {@code afterOwner}
   * (null = từ đầu) theo thứ tự của Postgres, tăng dần, tối đa {@code limit}.
   */
  List<UserId> ownersWithTombstonesBefore(Instant cutoff, @Nullable UserId afterOwner, int limit);

  /**
   * Dọn tombstone của {@code owner} ở mọi entity.
   *
   * @return {@code change_seq} lớn nhất đã xoá; rỗng khi không xoá gì
   */
  OptionalLong purgeTombstones(UserId owner, Instant cutoff);
}
