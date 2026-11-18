package com.uit.finance.modules.identity.adapter.in.scheduler;

import com.uit.finance.modules.identity.adapter.config.IdentityProperties;
import com.uit.finance.modules.identity.application.port.in.FinalizeUserDeletionsUseCase;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mỗi phút hoàn tất các tài khoản đã yêu cầu xoá quá {@code purge-delay} (ADR-008 A7). Nhiều
 * instance cùng chạy không sao: mỗi tài khoản bị khoá {@code SKIP LOCKED} trong lúc xử lý.
 */
@Component
class UserDeletionFinalizerJob {

  private final FinalizeUserDeletionsUseCase finalizer;
  private final IdentityProperties properties;
  private final Clock clock;

  UserDeletionFinalizerJob(
      FinalizeUserDeletionsUseCase finalizer, IdentityProperties properties, Clock clock) {
    this.finalizer = finalizer;
    this.properties = properties;
    this.clock = clock;
  }

  @Scheduled(
      fixedDelayString = "${app.identity.deletion.finalize-interval:1m}",
      initialDelayString = "${app.identity.deletion.finalize-interval:1m}")
  void finalizeDueDeletions() {
    finalizer.finalizeRequestedBefore(clock.instant().minus(properties.deletion().purgeDelay()));
  }
}
