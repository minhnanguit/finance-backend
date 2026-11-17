package com.uit.finance.modules.budget.domain.model;

import java.time.LocalDate;

/**
 * Giới hạn của ngân sách (ADR-007 §4, ADR-006 B4). Domain là lớp chặn chính và trả mã lỗi; {@code
 * CHECK} trong DB là lớp chặn thứ hai.
 */
public final class BudgetLimits {

  /** Cùng mức với số tiền giao dịch của ledger (ADR-005 §4). */
  public static final long MAX_AMOUNT_MINOR = 1_000_000_000_000_000L;

  /** Chỉ tính ngân sách **hợp lệ** chưa xoá (GS8). */
  public static final int MAX_BUDGETS_PER_USER = 100;

  public static final LocalDate EARLIEST_MONTH = LocalDate.of(2000, 1, 1);

  private BudgetLimits() {}

  /** Tháng UTC hiện tại + 1: dung sai cho máy ở múi giờ đi trước UTC. */
  public static LocalDate latestMonth(LocalDate todayUtc) {
    return todayUtc.withDayOfMonth(1).plusMonths(1);
  }
}
