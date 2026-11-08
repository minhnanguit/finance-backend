package com.uit.finance.modules.budget.domain.model;

import com.uit.finance.modules.budget.domain.exception.InvalidBudgetFieldException;
import com.uit.finance.shared.kernel.Ensure;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Tháng bắt đầu áp dụng, lưu là ngày 1 của tháng (ADR-007 G4). */
public record BudgetMonth(LocalDate firstDay) {

  private static final String FIELD = "startMonth";

  public BudgetMonth {
    Ensure.notNull(firstDay, FIELD);
    if (firstDay.getDayOfMonth() != 1 || firstDay.isBefore(BudgetLimits.EARLIEST_MONTH)) {
      throw new InvalidBudgetFieldException(FIELD);
    }
  }

  /** Tháng client gửi lên: thiếu, không phải ngày 1, hoặc quá {@link BudgetLimits#latestMonth}. */
  public static BudgetMonth requested(@Nullable LocalDate raw, LocalDate todayUtc) {
    if (raw == null) {
      throw new InvalidBudgetFieldException(FIELD);
    }
    BudgetMonth month = new BudgetMonth(raw);
    if (raw.isAfter(BudgetLimits.latestMonth(todayUtc))) {
      throw new InvalidBudgetFieldException(FIELD);
    }
    return month;
  }
}
