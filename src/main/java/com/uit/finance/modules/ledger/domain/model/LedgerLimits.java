package com.uit.finance.modules.ledger.domain.model;

import com.uit.finance.modules.ledger.domain.exception.LimitExceededException;
import java.time.LocalDate;
import java.time.Period;

/**
 * Giới hạn của ledger (ADR-005 §4, ADR-006 B4). Domain là lớp chặn chính và trả mã lỗi; {@code
 * CHECK} trong DB là lớp chặn thứ hai.
 */
public final class LedgerLimits {

  /** 10¹⁵ đơn vị nhỏ nhất: đủ cho mọi số tiền thật, cộng dồn vẫn còn xa giới hạn của long. */
  public static final long MAX_AMOUNT_MINOR = 1_000_000_000_000_000L;

  public static final int MAX_NAME_LENGTH = 50;
  public static final int MAX_PAYEE_LENGTH = 100;
  public static final int MAX_NOTE_LENGTH = 500;

  public static final int MAX_ACCOUNTS_PER_USER = 50;
  public static final int MAX_CATEGORIES_PER_USER = 300;

  public static final LocalDate EARLIEST_DATE = LocalDate.of(2000, 1, 1);
  public static final Period MAX_AHEAD = Period.ofYears(1);

  private LedgerLimits() {}

  /** {@code existing} = số ví chưa xoá của user, kể cả đã archive. */
  public static void ensureCanAddAccount(long existing) {
    if (existing >= MAX_ACCOUNTS_PER_USER) {
      throw new LimitExceededException(LedgerEntity.ACCOUNT, MAX_ACCOUNTS_PER_USER);
    }
  }

  /** {@code existing} = số danh mục chưa xoá của user, kể cả đã archive. */
  public static void ensureCanAddCategory(long existing) {
    if (existing >= MAX_CATEGORIES_PER_USER) {
      throw new LimitExceededException(LedgerEntity.CATEGORY, MAX_CATEGORIES_PER_USER);
    }
  }

  static LocalDate latestDate(LocalDate today) {
    return today.plus(MAX_AHEAD);
  }
}
