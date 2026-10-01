package com.uit.finance.modules.ledger.domain.model.account;

import com.uit.finance.modules.ledger.domain.exception.CurrencyLockedException;
import com.uit.finance.modules.ledger.domain.exception.EntityDeletedException;
import com.uit.finance.modules.ledger.domain.exception.InUseException;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.modules.ledger.domain.model.shared.Referenceable;
import com.uit.finance.shared.kernel.AggregateRoot;
import com.uit.finance.shared.kernel.Ensure;
import com.uit.finance.shared.kernel.Money;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Ví của user. Số dư không lưu ở đây mà tính từ {@link AccountFlows} (ADR-005 D4).
 *
 * <p>Các method đổi trạng thái trả {@code false} khi không có gì đổi, để caller bỏ qua lần ghi và
 * không phát thêm số thứ tự sync.
 */
public final class Account extends AggregateRoot implements Referenceable {

  private final AccountId id;
  private final UserId owner;
  private AccountDetails details;
  private @Nullable Instant archivedAt;
  private @Nullable Instant deletedAt;

  private Account(
      AccountId id,
      UserId owner,
      AccountDetails details,
      @Nullable Instant archivedAt,
      @Nullable Instant deletedAt) {
    this.id = Ensure.notNull(id, "id");
    this.owner = Ensure.notNull(owner, "owner");
    this.details = Ensure.notNull(details, "details");
    this.archivedAt = archivedAt;
    this.deletedAt = deletedAt;
  }

  public static Account open(AccountId id, UserId owner, AccountDetails details) {
    return new Account(id, owner, details, null, null);
  }

  public static Account rehydrate(
      AccountId id,
      UserId owner,
      AccountDetails details,
      @Nullable Instant archivedAt,
      @Nullable Instant deletedAt) {
    return new Account(id, owner, details, archivedAt, deletedAt);
  }

  /**
   * Áp dữ liệu mới. {@code isReferenced} chỉ được gọi khi tiền tệ đổi, để lần sửa bình thường không
   * tốn query (ADR-005 D8).
   */
  public boolean revise(AccountDetails next, BooleanSupplier isReferenced) {
    requireNotDeleted();
    if (next.equals(details)) {
      return false;
    }
    if (!next.currency().equals(details.currency()) && isReferenced.getAsBoolean()) {
      throw new CurrencyLockedException(id.value());
    }
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

  /** Ví đang được giao dịch trỏ tới thì chỉ archive được (ADR-005 D7). Xoá lần nữa là no-op. */
  public boolean delete(Instant now, BooleanSupplier isReferenced) {
    if (isDeleted()) {
      return false;
    }
    if (isReferenced.getAsBoolean()) {
      throw new InUseException(LedgerEntity.ACCOUNT, id.value());
    }
    deletedAt = Ensure.notNull(now, "now");
    return true;
  }

  /** Số dư = ban đầu + thu − chi − chuyển đi + chuyển đến (ADR-005 §5). */
  public Money balance(AccountFlows flows) {
    if (!flows.accountId().equals(id)) {
      throw new IllegalArgumentException("flows belong to account " + flows.accountId());
    }
    Currency currency = currency();
    return details
        .openingBalance()
        .plus(Money.of(flows.incomeMinor(), currency))
        .minus(Money.of(flows.expenseMinor(), currency))
        .minus(Money.of(flows.transferOutMinor(), currency))
        .plus(Money.of(flows.transferInMinor(), currency));
  }

  public void requireNotDeleted() {
    if (isDeleted()) {
      throw new EntityDeletedException(LedgerEntity.ACCOUNT, id.value());
    }
  }

  public AccountId getId() {
    return id;
  }

  public UserId getOwner() {
    return owner;
  }

  public AccountDetails getDetails() {
    return details;
  }

  public Currency currency() {
    return details.currency();
  }

  public @Nullable Instant getArchivedAt() {
    return archivedAt;
  }

  public @Nullable Instant getDeletedAt() {
    return deletedAt;
  }

  @Override
  public LedgerEntity entity() {
    return LedgerEntity.ACCOUNT;
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
