package com.uit.finance.modules.recurring.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Dữ liệu khoản định kỳ client gửi lên, chưa kiểm tra (schema {@code RecurringData}). Luôn đủ mọi
 * field kể cả khi sửa; {@code revision} là revision client dựa vào (ADR-009 RS8).
 */
public record RecurringFields(
    @Nullable String name,
    @Nullable String type,
    @Nullable String currency,
    @Nullable LocalDate generationNotBefore,
    @Nullable LocalDate generationEndDate,
    @Nullable Integer remindDaysBefore,
    long revision,
    @Nullable List<RevisionFields> revisions) {

  /** Một phần tử {@code RecurrenceRevisionData}, chưa kiểm tra. */
  public record RevisionFields(
      @Nullable UUID id,
      @Nullable LocalDate effectiveFrom,
      @Nullable LocalDate anchorDate,
      @Nullable UUID accountId,
      @Nullable UUID categoryId,
      long amountMinor,
      @Nullable String note,
      @Nullable String frequency,
      int interval,
      @Nullable Integer weekday,
      @Nullable Integer month,
      @Nullable Integer dayOfMonth,
      boolean autoConfirm) {

    /** Không in số tiền, ghi chú (ADR-006 B8). */
    @Override
    public String toString() {
      return "RevisionFields[id=%s, effectiveFrom=%s, frequency=%s]"
          .formatted(id, effectiveFrom, frequency);
    }
  }

  /** Không in tên khoản (ADR-006 B8). */
  @Override
  public String toString() {
    return "RecurringFields[type=%s, currency=%s, revision=%d]".formatted(type, currency, revision);
  }
}
