package com.uit.finance.modules.ledger.application.port.in.transaction;

import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;

/** Xoá giao dịch (tombstone). Xoá lần nữa là no-op, không phát event lần hai. */
public interface DeleteTransactionUseCase {

  TransactionView delete(DeleteTransactionCommand command);

  record DeleteTransactionCommand(UserId userId, UUID transactionId) {}
}
