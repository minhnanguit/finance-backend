package com.uit.finance.modules.ledger.application.port.in.maintenance;

import com.uit.finance.shared.kernel.UserId;

/**
 * Xoá thật toàn bộ sổ của một tài khoản đã xoá (ADR-008 A7): giao dịch, danh mục, ví, kể cả
 * tombstone. Idempotent: chạy lại trên user đã dọn thì không xoá gì.
 */
public interface PurgeUserLedgerUseCase {

  /**
   * @return số dòng đã xoá (để log, không có dữ liệu)
   */
  long purge(UserId owner);
}
