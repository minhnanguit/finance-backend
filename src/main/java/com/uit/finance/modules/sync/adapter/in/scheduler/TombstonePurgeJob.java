package com.uit.finance.modules.sync.adapter.in.scheduler;

import com.uit.finance.modules.sync.adapter.config.SyncProperties;
import com.uit.finance.modules.sync.application.port.in.PurgeTombstonesUseCase;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dọn tombstone quá hạn mỗi ngày (ADR-008 T1). Nhiều instance cùng chạy cũng không sao: mỗi user
 * được dọn dưới khoá ghi của chính user đó, lần thứ hai không còn gì để xoá.
 */
@Component
class TombstonePurgeJob {

  private final PurgeTombstonesUseCase purge;
  private final SyncProperties properties;
  private final Clock clock;

  TombstonePurgeJob(PurgeTombstonesUseCase purge, SyncProperties properties, Clock clock) {
    this.purge = purge;
    this.properties = properties;
    this.clock = clock;
  }

  @Scheduled(cron = "${app.sync.tombstone-purge-cron:0 45 3 * * *}")
  void purgeExpired() {
    purge.purgeDeletedBefore(clock.instant().minus(properties.tombstoneRetention()));
  }
}
