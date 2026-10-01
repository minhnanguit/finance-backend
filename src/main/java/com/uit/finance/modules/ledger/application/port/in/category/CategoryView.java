package com.uit.finance.modules.ledger.application.port.in.category;

import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.modules.ledger.domain.model.category.CategoryDetails;
import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import com.uit.finance.modules.ledger.domain.model.category.ColorHex;
import com.uit.finance.modules.ledger.domain.model.category.IconName;
import com.uit.finance.modules.ledger.domain.model.category.TemplateKey;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Bản ghi danh mục hiện tại trên server. */
public record CategoryView(
    UUID id,
    String kind,
    String name,
    @Nullable UUID parentId,
    @Nullable String icon,
    @Nullable String color,
    @Nullable String templateKey,
    @Nullable Instant archivedAt,
    boolean deleted) {

  public static CategoryView from(Category category) {
    CategoryDetails details = category.getDetails();
    return new CategoryView(
        category.getId().value(),
        category.getKind().name(),
        details.name().value(),
        Optional.ofNullable(details.parentId()).map(CategoryId::value).orElse(null),
        Optional.ofNullable(details.icon()).map(IconName::value).orElse(null),
        Optional.ofNullable(details.color()).map(ColorHex::value).orElse(null),
        Optional.ofNullable(category.getTemplateKey()).map(TemplateKey::value).orElse(null),
        category.getArchivedAt(),
        category.isDeleted());
  }
}
