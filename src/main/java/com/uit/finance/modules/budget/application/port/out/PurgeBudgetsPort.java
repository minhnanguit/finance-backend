package com.uit.finance.modules.budget.application.port.out;

import com.uit.finance.shared.kernel.UserId;

/** Xoá thật mọi ngân sách của một user (tài khoản đã xoá, ADR-008 A7). */
public interface PurgeBudgetsPort {

  /**
   * Xoá cả bản ghi còn hiệu lực lẫn tombstone.
   *
   * @return số dòng đã xoá
   */
  long deleteEverythingOf(UserId owner);
}
