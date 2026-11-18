package com.uit.finance.modules.recurring.application.port.in;

import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;

/**
 * Xoá mềm khoản định kỳ (tombstone). Giao dịch đã sinh không bị xoá (ADR-009 R14). Xoá lại là
 * no-op; không có hoặc của user khác: {@code recurring.not_found}.
 */
public interface DeleteRecurringUseCase {

  RecurringView delete(DeleteRecurringCommand command);

  record DeleteRecurringCommand(UserId userId, UUID recurringId) {}
}
