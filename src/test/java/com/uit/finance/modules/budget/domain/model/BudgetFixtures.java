package com.uit.finance.modules.budget.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.uit.finance.shared.kernel.DomainException;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/** Dữ liệu dùng chung cho test của budget. */
public final class BudgetFixtures {

  public static final Instant NOW = Instant.parse("2026-10-17T08:00:00Z");
  public static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
  public static final LocalDate THIS_MONTH = LocalDate.of(2026, 10, 1);

  public static final UserId ANN = UserId.of("00000000-0000-0000-0000-00000000a001");
  public static final UserId BOB = UserId.of("00000000-0000-0000-0000-00000000b001");

  public static final Currency VND = Currency.getInstance("VND");
  public static final Currency USD = Currency.getInstance("USD");

  private BudgetFixtures() {}

  public static BudgetTerms terms(UUID categoryId, Currency currency, long amountMinor) {
    return new BudgetTerms(
        categoryId, currency, new BudgetAmount(amountMinor), new BudgetMonth(THIS_MONTH));
  }

  public static Budget budget(UserId owner, UUID categoryId, Currency currency) {
    return Budget.create(
        new BudgetId(UUID.randomUUID()), owner, terms(categoryId, currency, 3_000_000));
  }

  public static void assertRejected(ThrowingCallable call, String code) {
    Throwable thrown = catchThrowable(call);
    assertThat(thrown).isInstanceOf(DomainException.class);
    assertThat(((DomainException) thrown).code()).isEqualTo(code);
  }
}
