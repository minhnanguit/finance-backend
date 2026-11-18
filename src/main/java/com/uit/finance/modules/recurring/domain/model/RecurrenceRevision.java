package com.uit.finance.modules.recurring.domain.model;

import com.uit.finance.modules.recurring.domain.exception.InvalidRecurringFieldException;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Một phiên bản của khoản: áp từ {@code effectiveFrom} tới trước phiên bản kế tiếp (ADR-009 R2).
 * Nhịp lặp tính từ {@code anchorDate}, tách khỏi {@code effectiveFrom} (R4).
 */
public record RecurrenceRevision(
    UUID id,
    LocalDate effectiveFrom,
    LocalDate anchorDate,
    UUID accountId,
    UUID categoryId,
    long amountMinor,
    @Nullable String note,
    RecurrenceRule rule,
    boolean autoConfirm) {

  public RecurrenceRevision {
    if (id == null) {
      throw new InvalidRecurringFieldException("revisions.id");
    }
    if (effectiveFrom == null || effectiveFrom.isBefore(RecurringLimits.MIN_DATE)) {
      throw new InvalidRecurringFieldException("revisions.effectiveFrom");
    }
    if (anchorDate == null
        || anchorDate.isBefore(RecurringLimits.MIN_DATE)
        || anchorDate.isAfter(effectiveFrom)) {
      throw new InvalidRecurringFieldException("revisions.anchorDate");
    }
    if (accountId == null) {
      throw new InvalidRecurringFieldException("revisions.accountId");
    }
    if (categoryId == null) {
      throw new InvalidRecurringFieldException("revisions.categoryId");
    }
    if (amountMinor < 1 || amountMinor > RecurringLimits.MAX_AMOUNT_MINOR) {
      throw new InvalidRecurringFieldException("revisions.amountMinor");
    }
    if (note != null && note.length() > RecurringLimits.MAX_NOTE_LENGTH) {
      throw new InvalidRecurringFieldException("revisions.note");
    }
    if (rule == null) {
      throw new InvalidRecurringFieldException("revisions.frequency");
    }
  }

  /** Không in số tiền, ghi chú (ADR-006 B8). */
  @Override
  public String toString() {
    return "RecurrenceRevision[id=%s, effectiveFrom=%s, rule=%s]"
        .formatted(id, effectiveFrom, rule);
  }
}
