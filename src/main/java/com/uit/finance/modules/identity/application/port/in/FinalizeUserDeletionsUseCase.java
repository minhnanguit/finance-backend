package com.uit.finance.modules.identity.application.port.in;

import java.time.Instant;

/**
 * Hoàn tất các tài khoản đã yêu cầu xoá trước {@code cutoff} (ADR-008 A7): chuyển sang {@code
 * DELETED} và phát {@code identity.user.deleted} để các module xoá thật dữ liệu. Nhiều instance
 * chạy cùng lúc không xử lý trùng một tài khoản.
 */
public interface FinalizeUserDeletionsUseCase {

  /**
   * @return số tài khoản đã hoàn tất
   */
  int finalizeRequestedBefore(Instant cutoff);
}
