package com.uit.finance.modules.ledger.adapter.out.persistence.category;

import com.uit.finance.modules.ledger.domain.model.category.CategoryKind;
import com.uit.finance.shared.persistence.AbstractJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Dòng của bảng {@code categories}. Insert đi bằng SQL trong {@link CategoryPersistenceAdapter}.
 */
@Entity
@Table(name = "categories")
class CategoryJpaEntity extends AbstractJpaEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private CategoryKind kind;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(name = "parent_id")
  private @Nullable UUID parentId;

  @Column(length = 50)
  private @Nullable String icon;

  @Column(length = 7)
  private @Nullable String color;

  @Column(name = "template_key", updatable = false, length = 40)
  private @Nullable String templateKey;

  @Column(name = "archived_at")
  private @Nullable Instant archivedAt;

  @Column(name = "change_seq", nullable = false)
  private long changeSeq;

  protected CategoryJpaEntity() {}

  UUID getUserId() {
    return userId;
  }

  CategoryKind getKind() {
    return kind;
  }

  String getName() {
    return name;
  }

  @Nullable UUID getParentId() {
    return parentId;
  }

  @Nullable String getIcon() {
    return icon;
  }

  @Nullable String getColor() {
    return color;
  }

  @Nullable String getTemplateKey() {
    return templateKey;
  }

  @Nullable Instant getArchivedAt() {
    return archivedAt;
  }

  long getChangeSeq() {
    return changeSeq;
  }

  /** {@code kind} và {@code templateKey} bất biến nên không có ở đây. */
  void apply(
      String name,
      @Nullable UUID parentId,
      @Nullable String icon,
      @Nullable String color,
      @Nullable Instant archivedAt) {
    this.name = name;
    this.parentId = parentId;
    this.icon = icon;
    this.color = color;
    this.archivedAt = archivedAt;
  }

  void assignChangeSeq(long changeSeq) {
    this.changeSeq = changeSeq;
  }
}
