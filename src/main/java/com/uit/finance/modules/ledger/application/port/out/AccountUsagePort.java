package com.uit.finance.modules.ledger.application.port.out;

import com.uit.finance.modules.ledger.domain.model.AccountId;
import com.uit.finance.shared.kernel.UserId;

public interface AccountUsagePort {

  /** Số ví chưa xoá của user, kể cả đã archive (giới hạn ADR-006 B4). */
  long countAccounts(UserId owner);

  /**
   * Có giao dịch chưa xoá nào trỏ vào ví, qua {@code account_id} hoặc {@code counter_account_id},
   * kể cả {@code DRAFT} (ADR-005 §3).
   */
  boolean isReferenced(UserId owner, AccountId id);
}
