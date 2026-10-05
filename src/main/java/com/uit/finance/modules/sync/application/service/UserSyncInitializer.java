package com.uit.finance.modules.sync.application.service;

import com.uit.finance.modules.sync.application.port.out.SyncHandlersPort;
import com.uit.finance.modules.sync.application.port.out.SyncStatePort;
import com.uit.finance.shared.kernel.UserId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lần sync đầu tiên của user: chạy {@code initialize} của mọi module (ledger seed danh mục mặc
 * định), trong cùng request (ADR-002 §5).
 *
 * <p>Đã khởi tạo thì chỉ tốn một câu đọc. Chưa thì khoá dòng của user rồi kiểm lại, nên 2 máy cùng
 * sync lần đầu chỉ khởi tạo một lần.
 */
@Service
@Transactional
class UserSyncInitializer {

  private final SyncStatePort state;
  private final SyncHandlersPort handlers;
  private final Clock clock;

  UserSyncInitializer(SyncStatePort state, SyncHandlersPort handlers, Clock clock) {
    this.state = state;
    this.handlers = handlers;
    this.clock = clock;
  }

  void ensureInitialized(UserId owner) {
    if (state.isInitialized(owner)) {
      return;
    }
    if (state.lockForInitialization(owner)) {
      handlers.initialize(owner);
      state.markInitialized(owner, clock.instant());
    }
  }
}
