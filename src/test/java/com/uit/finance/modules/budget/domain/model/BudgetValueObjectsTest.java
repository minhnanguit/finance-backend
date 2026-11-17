package com.uit.finance.modules.budget.domain.model;

import static com.uit.finance.modules.budget.domain.model.BudgetFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.uit.finance.modules.budget.domain.exception.InvalidBudgetFieldException;
import java.time.LocalDate;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BudgetValueObjectsTest {

  private static final UUID FOOD = UUID.randomUUID();

  private static void assertInvalid(ThrowingCallable call, String field) {
    Throwable thrown = catchThrowable(call);
    assertThat(thrown).isInstanceOf(InvalidBudgetFieldException.class);
    assertThat(((InvalidBudgetFieldException) thrown).field()).isEqualTo(field);
  }

  private static BudgetTerms parse(String currency, long amount, LocalDate month) {
    return BudgetTerms.parse(FOOD, currency, amount, month, TODAY);
  }

  @Test
  @DisplayName("số tiền từ 1 tới 10^15")
  void amountBounds() {
    assertThat(new BudgetAmount(1).minor()).isEqualTo(1);
    assertThat(new BudgetAmount(BudgetLimits.MAX_AMOUNT_MINOR).minor())
        .isEqualTo(BudgetLimits.MAX_AMOUNT_MINOR);
    assertInvalid(() -> new BudgetAmount(0), "amountMinor");
    assertInvalid(() -> new BudgetAmount(-1), "amountMinor");
    assertInvalid(() -> new BudgetAmount(BudgetLimits.MAX_AMOUNT_MINOR + 1), "amountMinor");
  }

  @Test
  @DisplayName("tháng là ngày 1, từ 01/2000 tới tháng UTC hiện tại + 1")
  void monthBounds() {
    assertThat(BudgetMonth.requested(LocalDate.of(2000, 1, 1), TODAY)).isNotNull();
    assertThat(BudgetMonth.requested(LocalDate.of(2026, 11, 1), TODAY)).isNotNull();
    assertInvalid(() -> BudgetMonth.requested(LocalDate.of(2026, 12, 1), TODAY), "startMonth");
    assertInvalid(() -> BudgetMonth.requested(LocalDate.of(1999, 12, 1), TODAY), "startMonth");
    assertInvalid(() -> BudgetMonth.requested(LocalDate.of(2026, 10, 2), TODAY), "startMonth");
    assertInvalid(() -> BudgetMonth.requested(null, TODAY), "startMonth");
  }

  @Test
  @DisplayName("ngày cuối năm UTC vẫn cho tháng 1 năm sau")
  void latestMonthCrossesYear() {
    LocalDate newYearsEve = LocalDate.of(2026, 12, 31);

    assertThat(BudgetMonth.requested(LocalDate.of(2027, 1, 1), newYearsEve)).isNotNull();
    assertInvalid(() -> BudgetMonth.requested(LocalDate.of(2027, 2, 1), newYearsEve), "startMonth");
  }

  @Test
  @DisplayName("tiền tệ ISO-4217 viết hoa, danh mục bắt buộc")
  void currencyAndCategory() {
    assertThat(parse("VND", 1, TODAY.withDayOfMonth(1)).currency().getCurrencyCode())
        .isEqualTo("VND");
    assertInvalid(() -> parse("vnd", 1, TODAY.withDayOfMonth(1)), "currency");
    assertInvalid(() -> parse("XYZ", 1, TODAY.withDayOfMonth(1)), "currency");
    assertInvalid(() -> parse(null, 1, TODAY.withDayOfMonth(1)), "currency");
    assertInvalid(
        () -> BudgetTerms.parse(null, "VND", 1, TODAY.withDayOfMonth(1), TODAY), "categoryId");
    assertInvalid(() -> BudgetId.of(null), "id");
  }

  @Test
  @DisplayName("toString không in số tiền (B8)")
  void toStringHidesAmount() {
    BudgetTerms terms = parse("VND", 123_456_789, TODAY.withDayOfMonth(1));

    assertThat(terms.toString()).doesNotContain("123456789");
    assertThat(terms.amount().toString()).doesNotContain("123456789");
  }
}
