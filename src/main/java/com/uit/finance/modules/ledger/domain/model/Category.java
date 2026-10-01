package com.uit.finance.modules.ledger.domain.model;

import com.uit.finance.modules.ledger.domain.exception.EntityDeletedException;
import com.uit.finance.modules.ledger.domain.exception.InUseException;
import com.uit.finance.modules.ledger.domain.exception.InvalidParentException;
import com.uit.finance.modules.ledger.domain.exception.KindImmutableException;
import com.uit.finance.shared.kernel.AggregateRoot;
import com.uit.finance.shared.kernel.Ensure;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Danh mục thu hoặc chi, tối đa 2 cấp. {@code kind} bất biến sau khi tạo (ADR-005 §2).
 *
 * <p>Cha được load qua {@code parents} và chỉ khi cha thật sự đổi, để lần sửa tên không tốn query.
 */
public final class Category extends AggregateRoot implements Referenceable {

  private final CategoryId id;
  private final UserId owner;
  private final CategoryKind kind;
  private final @Nullable TemplateKey templateKey;
  private CategoryDetails details;
  private @Nullable Instant archivedAt;
  private @Nullable Instant deletedAt;

  private Category(
      CategoryId id,
      UserId owner,
      CategoryKind kind,
      @Nullable TemplateKey templateKey,
      CategoryDetails details,
      @Nullable Instant archivedAt,
      @Nullable Instant deletedAt) {
    this.id = Ensure.notNull(id, "id");
    this.owner = Ensure.notNull(owner, "owner");
    this.kind = Ensure.notNull(kind, "kind");
    this.templateKey = templateKey;
    this.details = Ensure.notNull(details, "details");
    this.archivedAt = archivedAt;
    this.deletedAt = deletedAt;
  }

  public static Category create(
      CategoryId id,
      UserId owner,
      CategoryKind kind,
      CategoryDetails details,
      Function<CategoryId, Category> parents) {
    Category category = new Category(id, owner, kind, null, details, null, null);
    category.checkNewParent(null, details.parentId(), parents, () -> false);
    return category;
  }

  /** Bản copy danh mục mặc định cho một user, id cố định theo UUIDv5 (ADR-005 D9). */
  public static Category seed(UserId owner, DefaultCategoryTemplate template) {
    return new Category(
        CategoryId.forTemplate(owner, template.key()),
        owner,
        template.kind(),
        template.key(),
        template.details(),
        null,
        null);
  }

  public static Category rehydrate(
      CategoryId id,
      UserId owner,
      CategoryKind kind,
      @Nullable TemplateKey templateKey,
      CategoryDetails details,
      @Nullable Instant archivedAt,
      @Nullable Instant deletedAt) {
    return new Category(id, owner, kind, templateKey, details, archivedAt, deletedAt);
  }

  /**
   * Áp dữ liệu mới. Client gửi kèm {@code kind} để server phát hiện ý định đổi loại, thay vì lặng
   * lẽ bỏ qua.
   */
  public boolean revise(
      CategoryKind requestedKind,
      CategoryDetails next,
      Function<CategoryId, Category> parents,
      BooleanSupplier hasActiveChildren) {
    requireNotDeleted();
    if (requestedKind != kind) {
      throw new KindImmutableException(id.value());
    }
    if (next.equals(details)) {
      return false;
    }
    checkNewParent(details.parentId(), next.parentId(), parents, hasActiveChildren);
    details = next;
    return true;
  }

  public boolean changeArchived(boolean archived, Instant now) {
    requireNotDeleted();
    if (archived == isArchived()) {
      return false;
    }
    archivedAt = archived ? Ensure.notNull(now, "now") : null;
    return true;
  }

  /**
   * Còn giao dịch hoặc danh mục con thì chỉ archive được (ADR-005 D7). Xoá lần nữa là no-op.
   *
   * @param isInUse có giao dịch chưa xoá trỏ vào, hoặc có danh mục con chưa xoá
   */
  public boolean delete(Instant now, BooleanSupplier isInUse) {
    if (isDeleted()) {
      return false;
    }
    if (isInUse.getAsBoolean()) {
      throw new InUseException(LedgerEntity.CATEGORY, id.value());
    }
    deletedAt = Ensure.notNull(now, "now");
    return true;
  }

  public void requireNotDeleted() {
    if (isDeleted()) {
      throw new EntityDeletedException(LedgerEntity.CATEGORY, id.value());
    }
  }

  private void checkNewParent(
      @Nullable CategoryId current,
      @Nullable CategoryId next,
      Function<CategoryId, Category> parents,
      BooleanSupplier hasActiveChildren) {
    if (next == null || next.equals(current)) {
      return;
    }
    if (next.equals(id)) {
      throw new InvalidParentException("a category cannot be its own parent");
    }
    if (current == null && hasActiveChildren.getAsBoolean()) {
      throw new InvalidParentException("a category that has children cannot become a child");
    }
    Category parent = parents.apply(next);
    References.requireVisible(owner, parent);
    if (parent.details.parentId() != null) {
      throw new InvalidParentException("categories are limited to two levels");
    }
    if (parent.kind != kind) {
      throw new InvalidParentException("parent must have the same kind");
    }
    References.requireAttachable(parent, true);
  }

  public CategoryId getId() {
    return id;
  }

  public UserId getOwner() {
    return owner;
  }

  public CategoryKind getKind() {
    return kind;
  }

  public @Nullable TemplateKey getTemplateKey() {
    return templateKey;
  }

  public CategoryDetails getDetails() {
    return details;
  }

  public @Nullable Instant getArchivedAt() {
    return archivedAt;
  }

  public @Nullable Instant getDeletedAt() {
    return deletedAt;
  }

  @Override
  public LedgerEntity entity() {
    return LedgerEntity.CATEGORY;
  }

  @Override
  public UUID uuid() {
    return id.value();
  }

  @Override
  public boolean isOwnedBy(UserId user) {
    return owner.equals(user);
  }

  @Override
  public boolean isArchived() {
    return archivedAt != null;
  }

  @Override
  public boolean isDeleted() {
    return deletedAt != null;
  }
}
