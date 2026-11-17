package com.uit.finance.modules.sync.application.port.in;

import java.time.Instant;

/**
 * Xoá thật tombstone bị xoá trước {@code cutoff} (90 ngày, ADR-008 T1) và nâng mốc dọn của từng
 * user (T2), để bảng không phình mãi vì bản ghi đã xoá. Client có cursor cũ hơn mốc nhận {@code
 * 410}.
 */
public interface PurgeTombstonesUseCase {

  /**
   * @return số user đã được dọn
   */
  int purgeDeletedBefore(Instant cutoff);
}
