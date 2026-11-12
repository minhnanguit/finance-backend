package com.uit.finance.modules.ledger.application.port.in.maintenance;

import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

/**
 * Dọn tombstone quá hạn (ADR-008 T1). Module sync điều phối qua SPI {@code SyncHandler}: biết khi
 * nào dọn và ghi mốc cho cursor; ledger biết dòng nào được phép xoá.
 */
public interface TombstoneMaintenanceUseCase {

  /**
   * Chủ có tombstone của {@code entity} bị xoá trước {@code deletedBefore}, id lớn hơn {@code
   * afterOwner} theo thứ tự của Postgres, tăng dần, tối đa {@code limit}.
   */
  List<UserId> ownersWithTombstones(
      LedgerEntity entity, Instant deletedBefore, @Nullable UserId afterOwner, int limit);

  /**
   * Xoá thật tombstone của {@code owner} bị xoá trước {@code deletedBefore} mà không còn dòng nào
   * trỏ tới (FK trong module).
   *
   * @return {@code change_seq} lớn nhất đã xoá; rỗng khi không xoá gì
   */
  OptionalLong purgeTombstones(LedgerEntity entity, UserId owner, Instant deletedBefore);
}
