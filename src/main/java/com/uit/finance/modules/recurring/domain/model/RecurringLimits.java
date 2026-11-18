package com.uit.finance.modules.recurring.domain.model;

import java.time.LocalDate;

/** Giới hạn của ADR-009 §7. Domain là nguồn sự thật; {@code CHECK} trong DB là lớp chặn thứ hai. */
public final class RecurringLimits {

  public static final int MAX_NAME_LENGTH = 50;
  public static final long MAX_AMOUNT_MINOR = 1_000_000_000_000_000L;
  public static final int MAX_NOTE_LENGTH = 500;
  public static final int MAX_INTERVAL = 12;
  public static final int MAX_REMIND_DAYS = 7;
  public static final int MAX_REVISIONS = 50;
  public static final int MAX_ACTIVE_PER_USER = 200;
  public static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);

  /** Ngày xa nhất so với hôm nay (UTC) cho {@code effectiveFrom}. */
  public static final int MAX_DAYS_AHEAD = 366;

  /** Dung sai quanh "hôm nay" UTC cho máy ở múi giờ lệch UTC (ADR-009 RS11, RS12). */
  public static final int TODAY_TOLERANCE_DAYS = 1;

  private RecurringLimits() {}
}
