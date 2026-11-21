package com.uit.finance.modules.recurring.application.service;

import com.uit.finance.modules.recurring.application.port.in.RecurringFields;
import com.uit.finance.modules.recurring.application.port.in.RecurringFields.RevisionFields;
import com.uit.finance.modules.recurring.domain.exception.InvalidRecurringFieldException;
import com.uit.finance.modules.recurring.domain.model.Frequency;
import com.uit.finance.modules.recurring.domain.model.RecurrenceRevision;
import com.uit.finance.modules.recurring.domain.model.RecurrenceRule;
import com.uit.finance.modules.recurring.domain.model.RecurringTerms;
import com.uit.finance.modules.recurring.domain.model.RecurringType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Dữ liệu thô → domain. Lỗi đầu tiên là {@code recurring.invalid_field} kèm tên field. */
final class RecurringParser {

  private RecurringParser() {}

  /** "Hôm nay" cho các cửa sổ ngày, theo UTC (ADR-009 RS11). */
  static LocalDate today(Clock clock) {
    return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  static RecurringTerms terms(RecurringFields fields) {
    List<RevisionFields> revisions = fields.revisions();
    if (revisions == null) {
      throw new InvalidRecurringFieldException("revisions");
    }
    return new RecurringTerms(
        fields.name(),
        enumValue(RecurringType.class, fields.type(), "type"),
        RecurringTerms.currency(fields.currency()),
        fields.generationNotBefore(),
        fields.generationEndDate(),
        fields.remindDaysBefore(),
        revisions.stream().map(RecurringParser::revision).toList());
  }

  private static RecurrenceRevision revision(RevisionFields raw) {
    return new RecurrenceRevision(
        raw.id(),
        raw.effectiveFrom(),
        raw.anchorDate(),
        raw.accountId(),
        raw.categoryId(),
        raw.amountMinor(),
        raw.note(),
        new RecurrenceRule(
            enumValue(Frequency.class, raw.frequency(), "revisions.frequency"),
            raw.interval(),
            raw.weekday(),
            raw.month(),
            raw.dayOfMonth()),
        raw.autoConfirm());
  }

  private static <E extends Enum<E>> E enumValue(
      Class<E> type, @Nullable String raw, String field) {
    if (raw == null) {
      throw new InvalidRecurringFieldException(field);
    }
    try {
      return Enum.valueOf(type, raw);
    } catch (IllegalArgumentException e) {
      throw new InvalidRecurringFieldException(field);
    }
  }
}
