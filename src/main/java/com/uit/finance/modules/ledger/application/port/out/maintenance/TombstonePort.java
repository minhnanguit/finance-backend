package com.uit.finance.modules.ledger.application.port.out.maintenance;

import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

/**
 * Tombstone quá hạn trên bảng của {@code entity}. {@code afterOwner} là dấu trang (keyset theo
 * {@code user_id}) nên cũng là tham số {@link UserId} mà luật #10 đòi: hàm này chỉ trả id chủ,
 * không trả dữ liệu.
 */
public interface TombstonePort {

  List<UserId> ownersWithTombstonesBefore(
      LedgerEntity entity, Instant cutoff, @Nullable UserId afterOwner, int limit);

  /** Bỏ qua tombstone còn bị dòng khác trỏ tới; lần dọn sau sẽ lấy. */
  OptionalLong purgeTombstones(LedgerEntity entity, UserId owner, Instant cutoff);
}
