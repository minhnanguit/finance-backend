package com.uit.finance.modules.budget.adapter.out.persistence;

import com.uit.finance.modules.budget.application.port.out.BudgetWriteLockPort;
import com.uit.finance.shared.kernel.UserId;
import com.uit.finance.shared.sync.ChangeSequencer;
import org.springframework.stereotype.Component;

/**
 * Khoá dòng {@code user_sync_state} của user tới hết transaction (ADR-007 GS7). Cùng khoá mà sync
 * push lấy ở ADR-002 §7 bước 1.0, nên consumer và push của cùng user chạy lần lượt.
 */
@Component
class BudgetWriteLockAdapter implements BudgetWriteLockPort {

  private final ChangeSequencer sequencer;

  BudgetWriteLockAdapter(ChangeSequencer sequencer) {
    this.sequencer = sequencer;
  }

  @Override
  public void lock(UserId owner) {
    sequencer.lock(owner);
  }
}
