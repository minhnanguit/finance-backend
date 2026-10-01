package com.uit.finance.modules.ledger.application.port.in.account;

import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;

/**
 * Xoá ví (tombstone). Ví đã có giao dịch: {@code ledger.in_use}, chỉ archive được (ADR-005 D7). Xoá
 * lần nữa là no-op.
 */
public interface DeleteAccountUseCase {

  AccountView delete(DeleteAccountCommand command);

  record DeleteAccountCommand(UserId userId, UUID accountId) {}
}
