package com.uit.finance.modules.budget.application.port.out;

import com.uit.finance.shared.kernel.UserId;

/**
 * Khoá các lần ghi của một user tới hết transaction (ADR-007 GS7). Mọi use case ghi ngân sách gọi
 * {@link #lock} đầu tiên, kể cả consumer, để không đua với sync push của cùng user. Adapter cài
 * bằng {@code ChangeSequencer.lock}; khoá lại trong cùng transaction là no-op.
 */
public interface BudgetWriteLockPort {

  void lock(UserId owner);
}
