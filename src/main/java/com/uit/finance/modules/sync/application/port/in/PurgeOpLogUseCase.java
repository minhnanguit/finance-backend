package com.uit.finance.modules.sync.application.port.in;

import java.time.Instant;

/** Xoá nhật ký op đã quá hạn giữ (30 ngày, ADR-002 §7). */
public interface PurgeOpLogUseCase {

  /**
   * @return số dòng đã xoá
   */
  int purgeProcessedBefore(Instant cutoff);
}
