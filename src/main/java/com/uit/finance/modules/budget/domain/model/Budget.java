package com.uit.finance.modules.budget.domain.model;

import com.uit.finance.modules.budget.domain.exception.BudgetDeletedException;
import com.uit.finance.modules.budget.domain.exception.ImmutableBudgetFieldException;
import com.uit.finance.shared.kernel.AggregateRoot;
import com.uit.finance.shared.kernel.Ensure;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Ngân sách của một danh mục chi cấp 1, một tiền tệ, lặp lại mỗi tháng từ {@code startMonth}
 * (ADR-007 G1, G4). Chỉ số tiền sửa được (G8). Không phát event: chưa module nào cần nghe.
 *
 * <p>Danh mục có hợp lệ hay không do application kiểm qua {@link CategoryState} lúc tạo và lúc đối
 * chiếu; aggregate không giữ tham chiếu tới ledger.
 */
public final class Budget extends AggregateRoot {

  private final BudgetId id;
  private final UserId owner;
  private BudgetTerms terms;
  private @Nullable Instant deletedAt;

  private Budget(BudgetId id, UserId owner, BudgetTerms terms, @Nullable Instant deletedAt) {
    this.id = Ensure.notNull(id, "id");
    this.owner = Ensure.notNull(owner, "owner");
    this.terms = Ensure.notNull(terms, "terms");
    this.deletedAt = deletedAt;
  }

  public static Budget create(BudgetId id, UserId owner, BudgetTerms terms) {
    return new Budget(id, owner, terms, null);
  }

  public static Budget rehydrate(
      BudgetId id, UserId owner, BudgetTerms terms, @Nullable Instant deletedAt) {
    return new Budget(id, owner, terms, deletedAt);
  }

  /**
   * Áp dữ liệu mới. Client luôn gửi đủ 4 field; field bất biến khác bản đang có là {@code
   * budget.immutable_field}, không lặng lẽ bỏ qua.
   *
   * @return {@code false} khi không có gì đổi
   */
  public boolean revise(BudgetTerms next) {
    requireNotDeleted();
    if (!next.categoryId().equals(terms.categoryId())) {
      throw new ImmutableBudgetFieldException("categoryId");
    }
    if (!next.currency().equals(terms.currency())) {
      throw new ImmutableBudgetFieldException("currency");
    }
    if (!next.startMonth().equals(terms.startMonth())) {
      throw new ImmutableBudgetFieldException("startMonth");
    }
    if (next.amount().equals(terms.amount())) {
      return false;
    }
    terms = next;
    return true;
  }

  /** Xoá mềm (tombstone). Xoá lần nữa là no-op. */
  public boolean delete(Instant now) {
    if (isDeleted()) {
      return false;
    }
    deletedAt = Ensure.notNull(now, "now");
    return true;
  }

  public void requireNotDeleted() {
    if (isDeleted()) {
      throw new BudgetDeletedException(id.value());
    }
  }

  public BudgetId getId() {
    return id;
  }

  public UserId getOwner() {
    return owner;
  }

  public BudgetTerms getTerms() {
    return terms;
  }

  public @Nullable Instant getDeletedAt() {
    return deletedAt;
  }

  public boolean isOwnedBy(UserId user) {
    return owner.equals(user);
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }
}
