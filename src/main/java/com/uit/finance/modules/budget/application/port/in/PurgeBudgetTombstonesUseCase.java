package com.uit.finance.modules.budget.application.port.in;

import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

/**
 * Dọn tombstone ngân sách quá hạn (ADR-008 T1). Module sync điều phối qua SPI {@code SyncHandler}:
 * biết khi nào dọn và ghi mốc cho cursor; budget biết dòng nào được xoá.
 */
public interface PurgeBudgetTombstonesUseCase {

  /**
   * Chủ có tombstone bị xoá trước {@code deletedBefore}, id lớn hơn {@code afterOwner} theo thứ tự
   * của Postgres, tăng dần, tối đa {@code limit}.
   */
  List<UserId> ownersWithTombstones(Instant deletedBefore, @Nullable UserId afterOwner, int limit);

  /**
   * Xoá thật tombstone của {@code owner} bị xoá trước {@code deletedBefore}.
   *
   * @return {@code change_seq} lớn nhất đã xoá; rỗng khi không xoá gì
   */
  OptionalLong purgeTombstones(UserId owner, Instant deletedBefore);
}
