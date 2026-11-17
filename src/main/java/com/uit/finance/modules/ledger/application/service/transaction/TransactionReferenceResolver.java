package com.uit.finance.modules.ledger.application.service.transaction;

import com.uit.finance.modules.ledger.application.port.out.account.LoadAccountPort;
import com.uit.finance.modules.ledger.application.port.out.category.LoadCategoryPort;
import com.uit.finance.modules.ledger.domain.exception.ReferencePendingException;
import com.uit.finance.modules.ledger.domain.model.account.Account;
import com.uit.finance.modules.ledger.domain.model.account.AccountId;
import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.modules.ledger.domain.model.transaction.TransactionDetails;
import com.uit.finance.modules.ledger.domain.model.transaction.TransactionReferences;
import com.uit.finance.shared.kernel.UserId;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Load ví và danh mục mà giao dịch trỏ tới, luôn theo chủ sở hữu (ADR-006 B1).
 *
 * <p>Không thấy → {@code ledger.reference_pending}: có thể chưa sync tới, có thể là id của user
 * khác, và kẻ dò id không phân biệt được hai trường hợp (B2). Thấy nhưng đã xoá thì domain trả
 * {@code ledger.not_found}.
 */
@Component
class TransactionReferenceResolver {

  private final LoadAccountPort loadAccount;
  private final LoadCategoryPort loadCategory;

  TransactionReferenceResolver(LoadAccountPort loadAccount, LoadCategoryPort loadCategory) {
    this.loadAccount = loadAccount;
    this.loadCategory = loadCategory;
  }

  TransactionReferences resolve(UserId owner, TransactionDetails details) {
    Account account = account(owner, details.accountId());
    Account counterAccount =
        details.counterAccountId() == null ? null : account(owner, details.counterAccountId());
    @Nullable Category category =
        details.categoryId() == null
            ? null
            : loadCategory
                .find(owner, details.categoryId())
                .orElseThrow(
                    () ->
                        new ReferencePendingException(
                            LedgerEntity.CATEGORY, details.categoryId().value()));
    return new TransactionReferences(account, counterAccount, category);
  }

  private Account account(UserId owner, AccountId id) {
    return loadAccount
        .find(owner, id)
        .orElseThrow(() -> new ReferencePendingException(LedgerEntity.ACCOUNT, id.value()));
  }
}
