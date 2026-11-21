package com.uit.finance.modules.recurring.application.port.in;

import com.uit.finance.shared.kernel.UserId;

/** Xoá thật mọi khoản định kỳ của tài khoản đã xoá (ADR-008 A7). */
public interface PurgeUserRecurringsUseCase {

  /**
   * @return số khoản đã xoá (timeline đi theo FK cascade)
   */
  long purge(UserId owner);
}
