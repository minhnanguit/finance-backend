package com.uit.finance.modules.sync.application.port.in;

import com.uit.finance.shared.kernel.UserId;

/** Xoá nhật ký op và trạng thái sync của tài khoản đã xoá (ADR-008 A7). Idempotent. */
public interface PurgeUserSyncDataUseCase {

  void purge(UserId owner);
}
