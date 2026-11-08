package com.uit.finance.modules.ledger.application.port.in.reference;

import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Trạng thái hiện tại của một danh mục, đủ để module khác kiểm tham chiếu. Không có tên, icon hay
 * màu (ADR-006 B8).
 *
 * @param kind {@code INCOME} hoặc {@code EXPENSE}
 * @param parentId {@code null} khi là danh mục gốc
 */
public record CategoryRef(
    UUID id, String kind, @Nullable UUID parentId, boolean archived, boolean deleted) {

  public static CategoryRef from(Category category) {
    return new CategoryRef(
        category.getId().value(),
        category.getKind().name(),
        Optional.ofNullable(category.getDetails().parentId()).map(CategoryId::value).orElse(null),
        category.isArchived(),
        category.isDeleted());
  }
}
