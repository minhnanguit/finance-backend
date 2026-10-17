package com.uit.finance.modules.budget.domain.model;

import com.uit.finance.modules.budget.domain.exception.InvalidBudgetFieldException;
import com.uit.finance.shared.kernel.Ensure;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Phần client gửi lên của một ngân sách. Chỉ {@code amount} sửa được; ba field còn lại bất biến
 * (ADR-007 G8).
 */
public record BudgetTerms(
    UUID categoryId, Currency currency, BudgetAmount amount, BudgetMonth startMonth) {

  private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");

  public BudgetTerms {
    Ensure.notNull(categoryId, "categoryId");
    Ensure.notNull(currency, "currency");
    Ensure.notNull(amount, "amount");
    Ensure.notNull(startMonth, "startMonth");
  }

  /** Kiểm từng field theo thứ tự; lỗi đầu tiên là {@code budget.invalid_field} kèm tên field. */
  public static BudgetTerms parse(
      @Nullable UUID categoryId,
      @Nullable String currency,
      long amountMinor,
      @Nullable LocalDate startMonth,
      LocalDate todayUtc) {
    if (categoryId == null) {
      throw new InvalidBudgetFieldException("categoryId");
    }
    return new BudgetTerms(
        categoryId,
        currency(currency),
        new BudgetAmount(amountMinor),
        BudgetMonth.requested(startMonth, todayUtc));
  }

  private static Currency currency(@Nullable String raw) {
    if (raw == null || !CURRENCY_CODE.matcher(raw).matches()) {
      throw new InvalidBudgetFieldException("currency");
    }
    try {
      return Currency.getInstance(raw);
    } catch (IllegalArgumentException e) {
      throw new InvalidBudgetFieldException("currency");
    }
  }

  /** Không in số tiền (ADR-006 B8). */
  @Override
  public String toString() {
    return "BudgetTerms[categoryId=%s, currency=%s, startMonth=%s]"
        .formatted(categoryId, currency, startMonth.firstDay());
  }
}
