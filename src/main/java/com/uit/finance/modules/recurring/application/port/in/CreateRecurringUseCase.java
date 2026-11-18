package com.uit.finance.modules.recurring.application.port.in;

import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;

/**
 * Tạo khoản định kỳ theo id do client sinh, {@code revision} gửi lên phải là 0 (ADR-009 RS8).
 *
 * <ul>
 *   <li>Id của user khác: {@code recurring.not_found}, không ghi đè (ADR-006 B1).
 *   <li>Ví/danh mục chưa thấy: {@code recurring.reference_pending}; không dùng được: {@code
 *       recurring.invalid_account} / {@code recurring.invalid_category} / {@code
 *       recurring.currency_mismatch}; quá giới hạn: {@code recurring.limit_reached}.
 * </ul>
 */
public interface CreateRecurringUseCase {

  RecurringView create(CreateRecurringCommand command);

  record CreateRecurringCommand(UserId userId, UUID recurringId, RecurringFields fields) {}
}
