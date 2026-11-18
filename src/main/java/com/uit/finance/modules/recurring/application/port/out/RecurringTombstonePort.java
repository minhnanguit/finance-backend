package com.uit.finance.modules.recurring.application.port.out;

import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

/**
 * Tombstone quá hạn trên {@code recurrings}. {@code afterOwner} là dấu trang (keyset theo {@code
 * user_id}) nên cũng là tham số {@link UserId} mà luật #10 đòi: hàm này chỉ trả id chủ, không trả
 * dữ liệu.
 */
public interface RecurringTombstonePort {

  List<UserId> ownersWithTombstonesBefore(Instant cutoff, @Nullable UserId afterOwner, int limit);

  /**
   * Xoá thật tombstone của {@code owner} bị xoá trước {@code cutoff}. Chỉ {@code
   * recurring_revisions} trỏ tới {@code recurrings} và đi theo {@code ON DELETE CASCADE}; giao dịch
   * sinh từ khoản định kỳ không có FK sang đây (ADR-009).
   *
   * @return {@code change_seq} lớn nhất đã xoá; rỗng khi không xoá gì
   */
  OptionalLong purgeTombstones(UserId owner, Instant cutoff);
}
