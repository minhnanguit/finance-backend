package com.uit.finance.modules.budget.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

final class BudgetTime {

  private BudgetTime() {}

  /** "Hôm nay" cho giới hạn {@code startMonth} tính theo UTC (ADR-007 §4). */
  static LocalDate today(Clock clock) {
    return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
  }
}
