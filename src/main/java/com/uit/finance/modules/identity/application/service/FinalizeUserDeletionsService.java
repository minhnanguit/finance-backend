package com.uit.finance.modules.identity.application.service;

import com.uit.finance.modules.identity.application.port.in.FinalizeUserDeletionsUseCase;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Chia việc theo lô, mỗi lô một transaction ở {@link FinalizeUserDeletionBatchService}: một lô lỗi
 * không kéo theo lô khác, và không giữ khoá lâu.
 */
@Service
class FinalizeUserDeletionsService implements FinalizeUserDeletionsUseCase {

  static final int BATCH_SIZE = 50;
  private static final Logger log = LoggerFactory.getLogger(FinalizeUserDeletionsService.class);

  private final FinalizeUserDeletionBatchService batch;

  FinalizeUserDeletionsService(FinalizeUserDeletionBatchService batch) {
    this.batch = batch;
  }

  @Override
  public int finalizeRequestedBefore(Instant cutoff) {
    int total = 0;
    int done;
    do {
      done = batch.finalizeBatch(cutoff, BATCH_SIZE);
      total += done;
    } while (done == BATCH_SIZE);
    if (total > 0) {
      log.info("account deletion finalized users={}", total);
    }
    return total;
  }
}
