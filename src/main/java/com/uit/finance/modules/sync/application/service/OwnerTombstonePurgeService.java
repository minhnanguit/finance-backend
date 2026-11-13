package com.uit.finance.modules.sync.application.service;

import com.uit.finance.modules.sync.application.port.out.SyncHandlersPort;
import com.uit.finance.modules.sync.application.port.out.SyncStatePort;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Một user trong một transaction (ADR-008 T2): khoá dãy số của user trước (cùng thứ tự khoá với
 * push, nên không deadlock với op đang ghi), xoá tombstone, rồi nâng mốc dọn. Xoá và mốc commit
 * cùng nhau: không có lúc tombstone đã mất mà mốc chưa lên.
 */
@Service
@Transactional
class OwnerTombstonePurgeService {

  private final SyncStatePort state;
  private final SyncHandlersPort handlers;

  OwnerTombstonePurgeService(SyncStatePort state, SyncHandlersPort handlers) {
    this.state = state;
    this.handlers = handlers;
  }

  void purge(UserId owner, Instant cutoff) {
    state.lockWrites(owner);
    handlers.purgeTombstones(owner, cutoff).ifPresent(seq -> state.recordPurged(owner, seq));
  }
}
