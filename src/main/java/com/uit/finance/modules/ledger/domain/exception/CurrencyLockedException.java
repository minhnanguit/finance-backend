package com.uit.finance.modules.ledger.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;
import java.util.UUID;

/** Đổi tiền tệ của ví đã có giao dịch (ADR-005 D8). */
public class CurrencyLockedException extends LedgerException {

  public static final String CODE = "ledger.currency_locked";

  public CurrencyLockedException(UUID accountId) {
    super(
        ErrorCategory.BUSINESS_RULE,
        CODE,
        "account " + accountId + " already has transactions; its currency is locked");
  }
}
