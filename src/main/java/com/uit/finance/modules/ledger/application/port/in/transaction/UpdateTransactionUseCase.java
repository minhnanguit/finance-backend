package com.uit.finance.modules.ledger.application.port.in.transaction;

import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;

/**
 * Sửa giao dịch. Không phải của mình: {@code ledger.not_found}. Đã xoá: {@code ledger.deleted}. Gửi
 * y nguyên dữ liệu cũ là no-op, không phát event.
 */
public interface UpdateTransactionUseCase {

  TransactionView update(UpdateTransactionCommand command);

  record UpdateTransactionCommand(UserId userId, UUID transactionId, TransactionFields fields) {}
}
