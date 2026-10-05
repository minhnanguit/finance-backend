package com.uit.finance.modules.sync.application.service;

import com.uit.finance.modules.sync.application.port.in.PullChangesUseCase;
import com.uit.finance.modules.sync.domain.exception.InvalidBatchException;
import com.uit.finance.modules.sync.domain.model.ChangePage;
import com.uit.finance.modules.sync.domain.model.Cursor;
import com.uit.finance.modules.sync.domain.model.SyncLimits;
import org.springframework.stereotype.Service;

/** Khởi tạo user nếu cần (transaction ghi), rồi đọc trang trong snapshot (transaction đọc). */
@Service
class PullChangesService implements PullChangesUseCase {

  private final UserSyncInitializer initializer;
  private final ChangePageReader reader;

  PullChangesService(UserSyncInitializer initializer, ChangePageReader reader) {
    this.initializer = initializer;
    this.reader = reader;
  }

  @Override
  public ChangePage pull(PullQuery query) {
    Cursor since = Cursor.parse(query.cursor());
    if (query.limit() < 1 || query.limit() > SyncLimits.MAX_PULL_LIMIT) {
      throw new InvalidBatchException("limit must be between 1 and " + SyncLimits.MAX_PULL_LIMIT);
    }
    initializer.ensureInitialized(query.userId());
    return reader.read(query.userId(), since, query.limit());
  }
}
