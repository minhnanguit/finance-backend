package com.mosaicglobal.finance.shared.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {

  private static final Currency VND = Currency.getInstance("VND");
  private static final Currency USD = Currency.getInstance("USD");

  @Test
  void addsAndSubtractsInMinorUnits() {
    Money a = Money.of(1_000, VND);
    Money b = Money.of(250, VND);

    assertThat(a.plus(b)).isEqualTo(Money.of(1_250, VND));
    assertThat(a.minus(b)).isEqualTo(Money.of(750, VND));
    assertThat(b.minus(a).isNegative()).isTrue();
  }

  @Test
  void rejectsMixedCurrencies() {
    assertThatThrownBy(() -> Money.of(1, VND).plus(Money.of(1, USD)))
        .isInstanceOf(CurrencyMismatchException.class)
        .satisfies(
            e -> assertThat(((DomainException) e).code()).isEqualTo("money.currency_mismatch"));
  }

  @Test
  void convertsDecimalsWithoutFloatingPoint() {
    assertThat(Money.fromDecimal(new BigDecimal("12.34"), USD)).isEqualTo(Money.of(1_234, USD));
    assertThat(Money.of(1_234, USD).toDecimal()).isEqualByComparingTo("12.34");
    assertThat(Money.fromDecimal(new BigDecimal("15000"), VND)).isEqualTo(Money.of(15_000, VND));
  }

  @Test
  void comparesWithinSameCurrency() {
    assertThat(Money.of(5, USD)).isGreaterThan(Money.of(4, USD));
    assertThat(Money.zero(USD).isZero()).isTrue();
  }
}
