package com.uit.finance.modules.sync.adapter.in.scheduler;

import com.uit.finance.modules.sync.adapter.config.SyncProperties;
import com.uit.finance.modules.sync.application.port.in.PurgeOpLogUseCase;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dọn {@code sync_ops} quá hạn mỗi ngày. Nhiều instance cùng chạy cũng không sao: xoá là
 * idempotent, mỗi lô một câu lệnh ngắn.
 */
@Component
class OpLogPurgeJob {

  private static final Logger log = LoggerFactory.getLogger(OpLogPurgeJob.class);

  private final PurgeOpLogUseCase purge;
  private final SyncProperties properties;
  private final Clock clock;

  OpLogPurgeJob(PurgeOpLogUseCase purge, SyncProperties properties, Clock clock) {
    this.purge = purge;
    this.properties = properties;
    this.clock = clock;
  }

  @Scheduled(cron = "${app.sync.op-log-purge-cron:0 30 3 * * *}")
  void purgeExpired() {
    int deleted = purge.purgeProcessedBefore(clock.instant().minus(properties.opLogRetention()));
    log.info("sync op log purge deleted={}", deleted);
  }
}
