package com.uit.finance.modules.budget.application.port.out;

import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

/**
 * Tombstone quá hạn trên {@code budgets}. {@code afterOwner} là dấu trang (keyset theo {@code
 * user_id}) nên cũng là tham số {@link UserId} mà luật #10 đòi: hàm này chỉ trả id chủ, không trả
 * dữ liệu.
 */
public interface BudgetTombstonePort {

  List<UserId> ownersWithTombstonesBefore(Instant cutoff, @Nullable UserId afterOwner, int limit);

  /**
   * Xoá thật tombstone của {@code owner} bị xoá trước {@code cutoff}. Không bảng nào trỏ tới {@code
   * budgets} (ADR-007), nên không cần chờ dòng khác.
   *
   * @return {@code change_seq} lớn nhất đã xoá; rỗng khi không xoá gì
   */
  OptionalLong purgeTombstones(UserId owner, Instant cutoff);
}
