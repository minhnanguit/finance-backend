package com.uit.finance.modules.recurring.application.port.out;

import com.uit.finance.shared.kernel.UserId;

/**
 * Khoá các lần ghi của một user tới hết transaction (ADR-009 RS14). Mọi use case ghi gọi {@link
 * #lock} đầu tiên. Adapter cài bằng {@code ChangeSequencer.lock}; với sync push đã khoá ở {@code
 * ApplyOpService} nên khoá lại là no-op.
 */
public interface RecurringWriteLockPort {

  void lock(UserId owner);
}
