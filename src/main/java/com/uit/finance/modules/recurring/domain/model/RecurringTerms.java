package com.uit.finance.modules.recurring.domain.model;

import com.uit.finance.modules.recurring.domain.exception.InvalidRecurringFieldException;
import java.time.LocalDate;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Dữ liệu client gửi lên của một khoản, trừ {@code revision} (điều kiện ghi). Chỉ kiểm hình dạng;
 * luật so với bản đang có (bất biến, cửa sổ ngày) nằm ở {@link Recurring}.
 */
public record RecurringTerms(
    String name,
    RecurringType type,
    Currency currency,
    LocalDate generationNotBefore,
    @Nullable LocalDate generationEndDate,
    @Nullable Integer remindDaysBefore,
    List<RecurrenceRevision> revisions) {

  private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");

  public RecurringTerms {
    name = name == null ? null : name.strip();
    if (name == null || name.isEmpty() || name.length() > RecurringLimits.MAX_NAME_LENGTH) {
      throw new InvalidRecurringFieldException("name");
    }
    if (type == null) {
      throw new InvalidRecurringFieldException("type");
    }
    if (currency == null) {
      throw new InvalidRecurringFieldException("currency");
    }
    if (generationNotBefore == null || generationNotBefore.isBefore(RecurringLimits.MIN_DATE)) {
      throw new InvalidRecurringFieldException("generationNotBefore");
    }
    if (generationEndDate != null && generationEndDate.isBefore(RecurringLimits.MIN_DATE)) {
      throw new InvalidRecurringFieldException("generationEndDate");
    }
    if (remindDaysBefore != null
        && (remindDaysBefore < 0 || remindDaysBefore > RecurringLimits.MAX_REMIND_DAYS)) {
      throw new InvalidRecurringFieldException("remindDaysBefore");
    }
    if (revisions == null
        || revisions.isEmpty()
        || revisions.size() > RecurringLimits.MAX_REVISIONS) {
      throw new InvalidRecurringFieldException("revisions");
    }
    revisions = List.copyOf(revisions);
    Set<UUID> ids = new HashSet<>();
    for (int i = 0; i < revisions.size(); i++) {
      if (!ids.add(revisions.get(i).id())) {
        throw new InvalidRecurringFieldException("revisions.id");
      }
      if (i > 0
          && !revisions.get(i).effectiveFrom().isAfter(revisions.get(i - 1).effectiveFrom())) {
        throw new InvalidRecurringFieldException("revisions.effectiveFrom");
      }
    }
  }

  /** Mã ISO-4217 → {@link Currency}; sai là {@code recurring.invalid_field}. */
  public static Currency currency(@Nullable String raw) {
    if (raw == null || !CURRENCY_CODE.matcher(raw).matches()) {
      throw new InvalidRecurringFieldException("currency");
    }
    try {
      return Currency.getInstance(raw);
    } catch (IllegalArgumentException e) {
      throw new InvalidRecurringFieldException("currency");
    }
  }

  /** Không in tên khoản, số tiền, ghi chú (ADR-006 B8). */
  @Override
  public String toString() {
    return "RecurringTerms[type=%s, currency=%s, revisions=%d]"
        .formatted(type, currency, revisions.size());
  }
}
