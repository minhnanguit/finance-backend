package com.uit.finance.modules.budget.application.port.in;

import com.uit.finance.shared.kernel.UserId;

/**
 * Xoá thật toàn bộ ngân sách của một tài khoản đã xoá (ADR-008 A7), kể cả tombstone. Idempotent:
 * chạy lại trên user đã dọn thì không xoá gì.
 */
public interface PurgeUserBudgetsUseCase {

  /**
   * @return số dòng đã xoá (để log, không có dữ liệu)
   */
  long purge(UserId owner);
}
