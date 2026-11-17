package com.uit.finance.modules.ledger.domain.model.account;

import com.uit.finance.shared.kernel.Ensure;

/**
 * Tổng tiền đi qua một ví, chỉ tính giao dịch {@code CONFIRMED} chưa xoá (ADR-005 §5). Adapter tính
 * bằng SQL SUM, không load từng giao dịch lên.
 */
public record AccountFlows(
    AccountId accountId,
    long incomeMinor,
    long expenseMinor,
    long transferOutMinor,
    long transferInMinor) {

  public AccountFlows {
    Ensure.notNull(accountId, "accountId");
    if (incomeMinor < 0 || expenseMinor < 0 || transferOutMinor < 0 || transferInMinor < 0) {
      throw new IllegalArgumentException("flows are sums of positive amounts");
    }
  }

  public static AccountFlows none(AccountId accountId) {
    return new AccountFlows(accountId, 0, 0, 0, 0);
  }
}
