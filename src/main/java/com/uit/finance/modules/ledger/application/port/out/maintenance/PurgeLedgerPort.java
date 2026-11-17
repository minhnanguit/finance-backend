package com.uit.finance.modules.ledger.application.port.out.maintenance;

import com.uit.finance.shared.kernel.UserId;

/** Xoá thật mọi dòng ledger của một user (tài khoản đã xoá, ADR-008 A7). */
public interface PurgeLedgerPort {

  /**
   * Xoá theo thứ tự FK: giao dịch → danh mục → ví.
   *
   * @return tổng số dòng đã xoá
   */
  long deleteEverythingOf(UserId owner);
}
