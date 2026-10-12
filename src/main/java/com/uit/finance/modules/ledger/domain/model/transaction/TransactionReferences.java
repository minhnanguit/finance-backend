package com.uit.finance.modules.ledger.domain.model.transaction;

import com.uit.finance.modules.ledger.domain.model.account.Account;
import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.shared.kernel.Ensure;
import org.jspecify.annotations.Nullable;

/**
 * Ví và danh mục mà một giao dịch trỏ tới, đã được load theo chủ sở hữu (ADR-006 B1).
 *
 * @param counterAccount ví đến, chỉ có khi chuyển tiền
 * @param category chỉ có khi thu hoặc chi
 */
public record TransactionReferences(
    Account account, @Nullable Account counterAccount, @Nullable Category category) {

  public TransactionReferences {
    Ensure.notNull(account, "account");
  }
}
