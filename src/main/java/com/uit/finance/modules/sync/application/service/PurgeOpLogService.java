package com.uit.finance.modules.sync.application.service;

import com.uit.finance.modules.sync.application.port.in.PurgeOpLogUseCase;
import com.uit.finance.modules.sync.application.port.out.OpLogPort;
import java.time.Instant;
import org.springframework.stereotype.Service;

/** Xoá theo lô, mỗi lô một câu lệnh tự commit, để không giữ khoá lâu trên bảng đang được ghi. */
@Service
class PurgeOpLogService implements PurgeOpLogUseCase {

  static final int BATCH_SIZE = 5_000;

  private final OpLogPort opLog;

  PurgeOpLogService(OpLogPort opLog) {
    this.opLog = opLog;
  }

  @Override
  public int purgeProcessedBefore(Instant cutoff) {
    int total = 0;
    int deleted;
    do {
      deleted = opLog.purgeProcessedBefore(cutoff, BATCH_SIZE);
      total += deleted;
    } while (deleted == BATCH_SIZE);
    return total;
  }
}
