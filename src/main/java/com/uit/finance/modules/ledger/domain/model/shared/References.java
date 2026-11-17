package com.uit.finance.modules.ledger.domain.model.shared;

import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.shared.kernel.UserId;

/**
 * Luật chung khi trỏ tới ví hoặc danh mục.
 *
 * <p>Archive <b>không</b> chặn việc trỏ tới: nó chỉ ẩn bản ghi khỏi danh sách chọn ở client. Máy A
 * offline ghi vào ví mà máy B vừa archive vẫn phải được nhận, nếu không giao dịch bị từ chối rồi
 * mất khỏi máy A (ADR-005 §3).
 */
public final class References {

  private References() {}

  /**
   * Của user khác hoặc đã xoá đều là "không tồn tại" (ADR-006 B1, B2). Port đã load theo chủ sở
   * hữu; đây là lớp chặn thứ hai.
   */
  public static void requireVisible(UserId owner, Referenceable target) {
    if (!target.isOwnedBy(owner) || target.isDeleted()) {
      throw new LedgerNotFoundException(target.entity(), target.uuid());
    }
  }
}
