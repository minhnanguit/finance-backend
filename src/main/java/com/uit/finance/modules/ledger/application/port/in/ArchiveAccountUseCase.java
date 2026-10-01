package com.uit.finance.modules.ledger.application.port.in;

import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;

/** Ẩn hoặc hiện lại ví. Ví archive vẫn giữ lịch sử, nhưng không nhận giao dịch mới. */
public interface ArchiveAccountUseCase {

  AccountView setArchived(ArchiveAccountCommand command);

  record ArchiveAccountCommand(UserId userId, UUID accountId, boolean archived) {}
}
