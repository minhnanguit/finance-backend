package com.uit.finance.modules.ledger.application.service.shared;

import com.uit.finance.modules.ledger.application.port.in.account.AccountFields;
import com.uit.finance.modules.ledger.application.port.in.category.CategoryFields;
import com.uit.finance.modules.ledger.application.port.in.transaction.TransactionFields;
import com.uit.finance.modules.ledger.domain.model.account.AccountDetails;
import com.uit.finance.modules.ledger.domain.model.category.CategoryDetails;
import com.uit.finance.modules.ledger.domain.model.category.CategoryKind;
import com.uit.finance.modules.ledger.domain.model.transaction.TransactionDetails;

/** Dữ liệu thô từ client → value object của domain. Domain kiểm tra, mapper chỉ chuyển tiếp. */
public final class LedgerCommandMapper {

  private LedgerCommandMapper() {}

  public static AccountDetails toDetails(AccountFields fields) {
    return AccountDetails.parse(
        fields.name(),
        fields.type(),
        fields.currency(),
        fields.openingBalanceMinor(),
        fields.sortOrder());
  }

  public static CategoryKind toKind(CategoryFields fields) {
    return CategoryKind.parse(fields.kind());
  }

  public static CategoryDetails toDetails(CategoryFields fields) {
    return CategoryDetails.parse(fields.name(), fields.parentId(), fields.icon(), fields.color());
  }

  public static TransactionDetails toDetails(TransactionFields fields) {
    return TransactionDetails.parse(
        fields.type(),
        fields.status(),
        fields.accountId(),
        fields.counterAccountId(),
        fields.amountMinor(),
        fields.currency(),
        fields.categoryId(),
        fields.occurredOn(),
        fields.occurredAt(),
        fields.payee(),
        fields.note());
  }
}
