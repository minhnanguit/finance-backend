package com.uit.finance.modules.ledger.domain.model.transaction;

import com.uit.finance.modules.ledger.domain.model.category.CategoryKind;
import com.uit.finance.modules.ledger.domain.model.shared.Fields;
import org.jspecify.annotations.Nullable;

/**
 * Chiều của tiền. Số tiền luôn dương, chiều do type quyết định (ADR-005 D1). Chuyển tiền là 1 dòng
 * {@code TRANSFER} (D2).
 */
public enum TransactionType {
  INCOME(CategoryKind.INCOME),
  EXPENSE(CategoryKind.EXPENSE),
  TRANSFER(null);

  private final @Nullable CategoryKind categoryKind;

  TransactionType(@Nullable CategoryKind categoryKind) {
    this.categoryKind = categoryKind;
  }

  public static TransactionType parse(@Nullable String raw) {
    return Fields.enumValue(TransactionType.class, raw, "type");
  }

  public boolean isTransfer() {
    return this == TRANSFER;
  }

  /** Loại danh mục bắt buộc cho thu/chi. Chuyển tiền không có danh mục. */
  public CategoryKind categoryKind() {
    if (categoryKind == null) {
      throw new IllegalStateException("A transfer has no category");
    }
    return categoryKind;
  }
}
