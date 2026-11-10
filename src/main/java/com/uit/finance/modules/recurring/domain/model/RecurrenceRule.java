package com.uit.finance.modules.recurring.domain.model;

import com.uit.finance.modules.recurring.domain.exception.InvalidRecurringFieldException;
import org.jspecify.annotations.Nullable;

/**
 * Lịch lặp của một phiên bản (ADR-008 R3). Field không dùng cho {@code frequency} phải {@code
 * null}.
 *
 * @param weekday ISO 1 = thứ Hai … 7 = Chủ Nhật, chỉ có khi {@code WEEKLY}
 * @param month 1–12, chỉ có khi {@code YEARLY}
 * @param dayOfMonth 1–31, có khi {@code MONTHLY} và {@code YEARLY}
 */
public record RecurrenceRule(
    Frequency frequency,
    int interval,
    @Nullable Integer weekday,
    @Nullable Integer month,
    @Nullable Integer dayOfMonth) {

  public RecurrenceRule {
    if (frequency == null) {
      throw new InvalidRecurringFieldException("revisions.frequency");
    }
    if (interval < 1 || interval > RecurringLimits.MAX_INTERVAL) {
      throw new InvalidRecurringFieldException("revisions.interval");
    }
    switch (frequency) {
      case WEEKLY -> {
        requireIn(weekday, 1, 7, "revisions.weekday");
        requireNull(month, "revisions.month");
        requireNull(dayOfMonth, "revisions.dayOfMonth");
      }
      case MONTHLY -> {
        requireNull(weekday, "revisions.weekday");
        requireNull(month, "revisions.month");
        requireIn(dayOfMonth, 1, 31, "revisions.dayOfMonth");
      }
      case YEARLY -> {
        requireNull(weekday, "revisions.weekday");
        requireIn(month, 1, 12, "revisions.month");
        requireIn(dayOfMonth, 1, 31, "revisions.dayOfMonth");
      }
    }
  }

  private static void requireIn(@Nullable Integer value, int min, int max, String field) {
    if (value == null || value < min || value > max) {
      throw new InvalidRecurringFieldException(field);
    }
  }

  private static void requireNull(@Nullable Integer value, String field) {
    if (value != null) {
      throw new InvalidRecurringFieldException(field);
    }
  }
}
