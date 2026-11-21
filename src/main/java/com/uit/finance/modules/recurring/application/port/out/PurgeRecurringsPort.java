package com.uit.finance.modules.recurring.application.port.out;

import com.uit.finance.shared.kernel.UserId;

/** Xoá thật mọi khoản định kỳ của một user (tài khoản đã xoá, ADR-008 A7). */
public interface PurgeRecurringsPort {

  /**
   * Xoá cả bản ghi còn hiệu lực lẫn tombstone; timeline đi theo FK cascade.
   *
   * @return số dòng {@code recurrings} đã xoá
   */
  long deleteEverythingOf(UserId owner);
}
