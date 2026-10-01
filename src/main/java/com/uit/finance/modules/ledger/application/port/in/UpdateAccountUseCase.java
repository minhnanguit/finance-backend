package com.uit.finance.modules.ledger.application.port.in;

import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;

/**
 * Sửa ví. Không phải của mình: {@code ledger.not_found}. Đã xoá: {@code ledger.deleted}. Đổi tiền
 * tệ khi đã có giao dịch: {@code ledger.currency_locked} (ADR-005 D8).
 */
public interface UpdateAccountUseCase {

  AccountView update(UpdateAccountCommand command);

  record UpdateAccountCommand(UserId userId, UUID accountId, AccountFields fields) {}
}
