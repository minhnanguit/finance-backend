package com.uit.finance.modules.recurring.application.port.in;

import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;

/**
 * Sửa khoản định kỳ có điều kiện (ADR-008 RS8, RS11–RS13). {@code revision} khác server: {@code
 * recurring.revision_conflict}. Sửa phần bất biến: {@code recurring.immutable_field}. Đã xoá:
 * {@code recurring.deleted}. Không có hoặc của user khác: {@code recurring.not_found}.
 */
public interface UpdateRecurringUseCase {

  RecurringView update(UpdateRecurringCommand command);

  record UpdateRecurringCommand(UserId userId, UUID recurringId, RecurringFields fields) {}
}
