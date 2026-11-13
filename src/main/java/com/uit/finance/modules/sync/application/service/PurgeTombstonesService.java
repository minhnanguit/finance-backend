package com.uit.finance.modules.sync.application.service;

import com.uit.finance.modules.sync.application.port.in.PurgeTombstonesUseCase;
import com.uit.finance.modules.sync.application.port.out.SyncHandlersPort;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Đi qua các user có tombstone quá hạn theo thứ tự id (keyset), mỗi user một transaction ở {@link
 * OwnerTombstonePurgeService}. Mỗi user được xử lý nhiều nhất một lần mỗi lượt chạy, nên tombstone
 * chưa xoá được (còn bị trỏ tới) không làm vòng lặp quay mãi. Một user lỗi không chặn user khác.
 */
@Service
class PurgeTombstonesService implements PurgeTombstonesUseCase {

  static final int OWNER_BATCH = 500;
  private static final Logger log = LoggerFactory.getLogger(PurgeTombstonesService.class);

  private final SyncHandlersPort handlers;
  private final OwnerTombstonePurgeService ownerPurge;

  PurgeTombstonesService(SyncHandlersPort handlers, OwnerTombstonePurgeService ownerPurge) {
    this.handlers = handlers;
    this.ownerPurge = ownerPurge;
  }

  @Override
  public int purgeDeletedBefore(Instant cutoff) {
    int owners = 0;
    int failed = 0;
    @Nullable UserId after = null;
    while (true) {
      List<UserId> batch = handlers.ownersWithTombstonesBefore(cutoff, after, OWNER_BATCH);
      for (UserId owner : batch) {
        try {
          ownerPurge.purge(owner, cutoff);
          owners++;
        } catch (RuntimeException e) {
          failed++;
          log.warn("tombstone purge failed user={} cause={}", owner, e.getClass().getName());
        }
      }
      if (batch.size() < OWNER_BATCH) {
        break;
      }
      after = batch.getLast();
    }
    log.info("sync tombstone purge users={} failed={}", owners, failed);
    return owners;
  }
}
