package com.uit.finance.modules.ledger.domain.model;

import com.uit.finance.modules.ledger.domain.exception.ArchivedException;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.shared.kernel.UserId;

/** Luật chung khi trỏ tới ví hoặc danh mục. */
final class References {

  private References() {}

  /**
   * Của user khác hoặc đã xoá đều là "không tồn tại" (ADR-006 B1, B2). Port đã load theo chủ sở
   * hữu; đây là lớp chặn thứ hai.
   */
  static void requireVisible(UserId owner, Referenceable target) {
    if (!target.isOwnedBy(owner) || target.isDeleted()) {
      throw new LedgerNotFoundException(target.entity(), target.uuid());
    }
  }

  /**
   * Chỉ chặn khi gắn mới vào bản ghi đã archive. Bản ghi cũ vẫn trỏ vào đó thì vẫn sửa được, để
   * user còn sửa được ghi chú của giao dịch cũ sau khi archive ví.
   */
  static void requireAttachable(Referenceable target, boolean newlyAttached) {
    if (newlyAttached && target.isArchived()) {
      throw new ArchivedException(target.entity(), target.uuid());
    }
  }
}
