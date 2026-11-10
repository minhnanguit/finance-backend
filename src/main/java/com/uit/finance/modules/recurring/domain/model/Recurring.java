package com.uit.finance.modules.recurring.domain.model;

import com.uit.finance.modules.recurring.domain.exception.ImmutableRecurringFieldException;
import com.uit.finance.modules.recurring.domain.exception.InvalidRecurringFieldException;
import com.uit.finance.modules.recurring.domain.exception.RecurringDeletedException;
import com.uit.finance.modules.recurring.domain.exception.RevisionConflictException;
import com.uit.finance.shared.kernel.AggregateRoot;
import com.uit.finance.shared.kernel.Ensure;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Khoản định kỳ: phần cố định + timeline phiên bản, ghi có điều kiện theo {@code revision} (ADR-008
 * R1–R11, RS8, RS11, RS12). Không phát event: chưa module nào cần nghe.
 *
 * <p>"Hôm nay" là ngày UTC do application truyền vào (Clock). Ranh giới "đã hiệu lực" là {@code
 * today − 1}: dung sai cho máy ở múi giờ lệch UTC.
 */
public final class Recurring extends AggregateRoot {

  private final RecurringId id;
  private final UserId owner;
  private RecurringTerms terms;
  private long revision;
  private @Nullable Instant deletedAt;

  private Recurring(
      RecurringId id,
      UserId owner,
      RecurringTerms terms,
      long revision,
      @Nullable Instant deletedAt) {
    this.id = Ensure.notNull(id, "id");
    this.owner = Ensure.notNull(owner, "owner");
    this.terms = Ensure.notNull(terms, "terms");
    this.revision = revision;
    this.deletedAt = deletedAt;
  }

  /**
   * Tạo mới với {@code revision = 1}. Phiên bản được phép bắt đầu trong quá khứ (khoản đã chạy từ
   * trước), nhưng không quá {@link RecurringLimits#MAX_DAYS_AHEAD} ngày tới (RS11).
   */
  public static Recurring create(
      RecurringId id, UserId owner, RecurringTerms terms, long expectedRevision, LocalDate today) {
    if (expectedRevision != 0) {
      throw new InvalidRecurringFieldException("revision");
    }
    LocalDate latest = today.plusDays(RecurringLimits.MAX_DAYS_AHEAD);
    for (RecurrenceRevision r : terms.revisions()) {
      if (r.effectiveFrom().isAfter(latest)) {
        throw new InvalidRecurringFieldException("revisions.effectiveFrom");
      }
    }
    if (terms.generationNotBefore().isAfter(latest)) {
      throw new InvalidRecurringFieldException("generationNotBefore");
    }
    return new Recurring(id, owner, terms, 1, null);
  }

  public static Recurring rehydrate(
      RecurringId id,
      UserId owner,
      RecurringTerms terms,
      long revision,
      @Nullable Instant deletedAt) {
    return new Recurring(id, owner, terms, revision, deletedAt);
  }

  /**
   * Áp bản mới nếu client dựa đúng {@code revision} hiện tại (RS8). Thành công thì {@code revision}
   * tăng đúng 1; không có gì đổi thì giữ nguyên và trả {@code false}.
   */
  public boolean revise(RecurringTerms next, long expectedRevision, LocalDate today) {
    requireNotDeleted();
    if (expectedRevision != revision) {
      throw new RevisionConflictException(id.value());
    }
    if (next.type() != terms.type()) {
      throw new ImmutableRecurringFieldException("type");
    }
    if (!next.currency().equals(terms.currency())) {
      throw new ImmutableRecurringFieldException("currency");
    }
    requireTimelineKeepsThePast(next.revisions(), today);
    requireNotBeforeOnlyGrows(next.generationNotBefore(), today);
    requireEndOnlyShrinks(next.generationEndDate(), today);
    if (next.equals(terms)) {
      return false;
    }
    terms = next;
    revision++;
    return true;
  }

  /**
   * Phiên bản trước {@code today − 1} phải giữ y nguyên; phiên bản thêm/sửa nằm trong {@code [today
   * − 1, today + 366]} (R10, RS11).
   */
  private void requireTimelineKeepsThePast(List<RecurrenceRevision> next, LocalDate today) {
    LocalDate boundary = today.minusDays(RecurringLimits.TODAY_TOLERANCE_DAYS);
    List<RecurrenceRevision> pastNow =
        terms.revisions().stream().filter(r -> r.effectiveFrom().isBefore(boundary)).toList();
    List<RecurrenceRevision> pastNext =
        next.stream().filter(r -> r.effectiveFrom().isBefore(boundary)).toList();
    if (!pastNow.equals(pastNext)) {
      throw new ImmutableRecurringFieldException("revisions");
    }
    LocalDate latest = today.plusDays(RecurringLimits.MAX_DAYS_AHEAD);
    for (RecurrenceRevision r : next) {
      if (r.effectiveFrom().isAfter(latest)) {
        throw new InvalidRecurringFieldException("revisions.effectiveFrom");
      }
    }
  }

  /** R8, RS12: chỉ tăng, giá trị mới tối đa {@code today + 1}. */
  private void requireNotBeforeOnlyGrows(LocalDate next, LocalDate today) {
    LocalDate current = terms.generationNotBefore();
    if (next.isBefore(current)) {
      throw new ImmutableRecurringFieldException("generationNotBefore");
    }
    if (next.isAfter(current)
        && next.isAfter(today.plusDays(RecurringLimits.TODAY_TOLERANCE_DAYS))) {
      throw new InvalidRecurringFieldException("generationNotBefore");
    }
  }

  /** R11, RS12: đã đặt thì không gỡ, không tăng; giá trị mới tối thiểu {@code today − 1}. */
  private void requireEndOnlyShrinks(@Nullable LocalDate next, LocalDate today) {
    LocalDate current = terms.generationEndDate();
    if (Objects.equals(current, next)) {
      return;
    }
    if (current != null && (next == null || next.isAfter(current))) {
      throw new ImmutableRecurringFieldException("generationEndDate");
    }
    if (next.isBefore(today.minusDays(RecurringLimits.TODAY_TOLERANCE_DAYS))) {
      throw new InvalidRecurringFieldException("generationEndDate");
    }
  }

  /**
   * Phiên bản thêm mới hoặc khác bản đang có: chỉ những phiên bản này phải kiểm tham chiếu ví/danh
   * mục (RS13). Phiên bản cũ được phép trỏ tới ví/danh mục đã archive hoặc xoá.
   */
  public List<RecurrenceRevision> changedRevisions(RecurringTerms next) {
    Set<RecurrenceRevision> existing = new HashSet<>(terms.revisions());
    return next.revisions().stream().filter(r -> !existing.contains(r)).toList();
  }

  /** Còn hiệu lực cho giới hạn số khoản (§7): chưa xoá và chưa kết thúc trước {@code today − 1}. */
  public boolean isActiveOn(LocalDate today) {
    LocalDate end = terms.generationEndDate();
    return !isDeleted()
        && (end == null || !end.isBefore(today.minusDays(RecurringLimits.TODAY_TOLERANCE_DAYS)));
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
      throw new RecurringDeletedException(id.value());
    }
  }

  public RecurringId getId() {
    return id;
  }

  public UserId getOwner() {
    return owner;
  }

  public RecurringTerms getTerms() {
    return terms;
  }

  public long getRevision() {
    return revision;
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
