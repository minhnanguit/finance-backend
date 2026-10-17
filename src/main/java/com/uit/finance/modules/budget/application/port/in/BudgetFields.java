package com.uit.finance.modules.budget.application.port.in;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Dữ liệu ngân sách client gửi lên, chưa kiểm tra. Luôn đủ 4 field, kể cả khi sửa. */
public record BudgetFields(
    @Nullable UUID categoryId,
    @Nullable String currency,
    long amountMinor,
    @Nullable LocalDate startMonth) {

  /** Không in số tiền (ADR-006 B8). */
  @Override
  public String toString() {
    return "BudgetFields[categoryId=%s, currency=%s, startMonth=%s]"
        .formatted(categoryId, currency, startMonth);
  }
}
