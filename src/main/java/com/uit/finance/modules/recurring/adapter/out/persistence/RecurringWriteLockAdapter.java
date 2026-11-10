package com.uit.finance.modules.recurring.adapter.out.persistence;

import com.uit.finance.modules.recurring.application.port.out.RecurringWriteLockPort;
import com.uit.finance.shared.kernel.UserId;
import com.uit.finance.shared.sync.ChangeSequencer;
import org.springframework.stereotype.Component;

/**
 * Khoá dòng {@code user_sync_state} của user tới hết transaction (ADR-008 RS14). Cùng khoá mà sync
 * push lấy ở ADR-002 §7 bước 1.0.
 */
@Component
class RecurringWriteLockAdapter implements RecurringWriteLockPort {

  private final ChangeSequencer sequencer;

  RecurringWriteLockAdapter(ChangeSequencer sequencer) {
    this.sequencer = sequencer;
  }

  @Override
  public void lock(UserId owner) {
    sequencer.lock(owner);
  }
}
