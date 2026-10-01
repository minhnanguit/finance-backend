package com.uit.finance.modules.ledger.application.port.in;

import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;

/**
 * Ghi giao dịch theo id do client sinh, cùng quy tắc gửi lại / id của người khác / đã xoá như
 * {@link CreateAccountUseCase}. Phát {@code ledger.transaction.recorded} một lần duy nhất.
 *
 * <p>Ví hoặc danh mục chưa thấy (chưa sync tới hoặc của user khác): {@code
 * ledger.reference_pending}. Của chính user nhưng đã xoá: {@code ledger.not_found} (ADR-006).
 */
public interface RecordTransactionUseCase {

  TransactionView record(RecordTransactionCommand command);

  record RecordTransactionCommand(UserId userId, UUID transactionId, TransactionFields fields) {}
}
