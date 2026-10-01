package com.uit.finance.modules.ledger.application.service;

import com.uit.finance.modules.ledger.application.port.in.AccountFields;
import com.uit.finance.modules.ledger.application.port.in.CategoryFields;
import com.uit.finance.modules.ledger.application.port.in.TransactionFields;
import com.uit.finance.modules.ledger.domain.model.AccountDetails;
import com.uit.finance.modules.ledger.domain.model.CategoryDetails;
import com.uit.finance.modules.ledger.domain.model.CategoryKind;
import com.uit.finance.modules.ledger.domain.model.TransactionDetails;

/** Dữ liệu thô từ client → value object của domain. Domain kiểm tra, mapper chỉ chuyển tiếp. */
final class LedgerCommandMapper {

  private LedgerCommandMapper() {}

  static AccountDetails toDetails(AccountFields fields) {
    return AccountDetails.parse(
        fields.name(),
        fields.type(),
        fields.currency(),
        fields.openingBalanceMinor(),
        fields.sortOrder());
  }

  static CategoryKind toKind(CategoryFields fields) {
    return CategoryKind.parse(fields.kind());
  }

  static CategoryDetails toDetails(CategoryFields fields) {
    return CategoryDetails.parse(fields.name(), fields.parentId(), fields.icon(), fields.color());
  }

  static TransactionDetails toDetails(TransactionFields fields) {
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
