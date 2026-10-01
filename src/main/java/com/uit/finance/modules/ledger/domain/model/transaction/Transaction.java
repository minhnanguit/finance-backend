package com.uit.finance.modules.ledger.domain.model.transaction;

import com.uit.finance.modules.ledger.domain.event.TransactionDeleted;
import com.uit.finance.modules.ledger.domain.event.TransactionRecorded;
import com.uit.finance.modules.ledger.domain.event.TransactionUpdated;
import com.uit.finance.modules.ledger.domain.exception.EntityDeletedException;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.shared.kernel.AggregateRoot;
import com.uit.finance.shared.kernel.Ensure;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Một dòng thu, chi hoặc chuyển tiền. Xoá là tombstone ({@code deletedAt}), không bao giờ xoá thật
 * trong plan này, để máy khác biết mà xoá theo (ADR-002).
 */
public final class Transaction extends AggregateRoot {

  private final TransactionId id;
  private final UserId owner;
  private TransactionDetails details;
  private @Nullable Instant deletedAt;

  private Transaction(
      TransactionId id, UserId owner, TransactionDetails details, @Nullable Instant deletedAt) {
    this.id = Ensure.notNull(id, "id");
    this.owner = Ensure.notNull(owner, "owner");
    this.details = Ensure.notNull(details, "details");
    this.deletedAt = deletedAt;
  }

  /**
   * @param today ngày hiện tại theo UTC, dùng cho giới hạn {@code occurredOn}
   */
  public static Transaction record(
      TransactionId id,
      UserId owner,
      TransactionDetails details,
      TransactionReferences references,
      LocalDate today,
      Instant now) {
    TransactionRules.check(owner, details, references, today, null);
    Transaction transaction = new Transaction(id, owner, details, null);
    transaction.registerEvent(TransactionRecorded.of(transaction, now));
    return transaction;
  }

  public static Transaction rehydrate(
      TransactionId id, UserId owner, TransactionDetails details, @Nullable Instant deletedAt) {
    return new Transaction(id, owner, details, deletedAt);
  }

  /**
   * Áp dữ liệu mới. {@code references} chỉ được load khi dữ liệu thật sự đổi, để client gửi lại y
   * nguyên không tốn query.
   */
  public boolean revise(
      TransactionDetails next,
      Supplier<TransactionReferences> references,
      LocalDate today,
      Instant now) {
    requireNotDeleted();
    if (next.equals(details)) {
      return false;
    }
    TransactionRules.check(owner, next, references.get(), today, details);
    details = next;
    registerEvent(TransactionUpdated.of(this, now));
    return true;
  }

  /** Xoá lần nữa là no-op, không phát event (ADR-002 §3). */
  public boolean delete(Instant now) {
    if (isDeleted()) {
      return false;
    }
    deletedAt = Ensure.notNull(now, "now");
    registerEvent(TransactionDeleted.of(this, now));
    return true;
  }

  public void requireNotDeleted() {
    if (isDeleted()) {
      throw new EntityDeletedException(LedgerEntity.TRANSACTION, id.value());
    }
  }

  public TransactionId getId() {
    return id;
  }

  public UserId getOwner() {
    return owner;
  }

  public TransactionDetails getDetails() {
    return details;
  }

  public @Nullable Instant getDeletedAt() {
    return deletedAt;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }
}
