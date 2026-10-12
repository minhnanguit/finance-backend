package com.uit.finance.modules.ledger.adapter.out.persistence.category;

import com.uit.finance.modules.ledger.adapter.out.persistence.support.SqlValues;
import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.modules.ledger.domain.model.category.CategoryDetails;
import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import com.uit.finance.modules.ledger.domain.model.category.ColorHex;
import com.uit.finance.modules.ledger.domain.model.category.IconName;
import com.uit.finance.modules.ledger.domain.model.category.TemplateKey;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerName;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

final class CategoryPersistenceMapper {

  private CategoryPersistenceMapper() {}

  static Category toDomain(CategoryJpaEntity entity) {
    return Category.rehydrate(
        new CategoryId(entity.getId()),
        new UserId(entity.getUserId()),
        entity.getKind(),
        Optional.ofNullable(entity.getTemplateKey()).map(TemplateKey::new).orElse(null),
        new CategoryDetails(
            new LedgerName(entity.getName()),
            Optional.ofNullable(entity.getParentId()).map(CategoryId::new).orElse(null),
            Optional.ofNullable(entity.getIcon()).map(IconName::new).orElse(null),
            Optional.ofNullable(entity.getColor()).map(ColorHex::new).orElse(null)),
        entity.getArchivedAt(),
        entity.getDeletedAt());
  }

  static void apply(Category category, CategoryJpaEntity entity) {
    CategoryDetails details = category.getDetails();
    entity.apply(
        details.name().value(),
        parentId(details),
        details.icon() == null ? null : details.icon().value(),
        details.color() == null ? null : details.color().value(),
        category.getArchivedAt());
    if (category.getDeletedAt() != null && !entity.isDeleted()) {
      entity.markDeleted(category.getDeletedAt());
    }
  }

  /** Tham số cho {@code CategoryPersistenceAdapter.INSERT}. */
  static MapSqlParameterSource insertParams(Category category, long changeSeq, Instant now) {
    CategoryDetails details = category.getDetails();
    return new SqlValues()
        .uuid("id", category.getId().value())
        .uuid("userId", category.getOwner().value())
        .text("kind", category.getKind().name())
        .text("name", details.name().value())
        .uuid("parentId", parentId(details))
        .text("icon", details.icon() == null ? null : details.icon().value())
        .text("color", details.color() == null ? null : details.color().value())
        .text(
            "templateKey",
            category.getTemplateKey() == null ? null : category.getTemplateKey().value())
        .timestamp("archivedAt", category.getArchivedAt())
        .number("changeSeq", changeSeq)
        .timestamp("now", now)
        .timestamp("deletedAt", category.getDeletedAt())
        .build();
  }

  private static @Nullable UUID parentId(CategoryDetails details) {
    return details.parentId() == null ? null : details.parentId().value();
  }
}
