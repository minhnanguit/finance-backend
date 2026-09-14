package com.mosaicglobal.finance.shared.kernel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Monetary amount stored in minor units (cents, đồng...) of an ISO-4217 currency.
 *
 * <p>Never uses floating point. Arithmetic across different currencies is rejected.
 */
public record Money(long amountMinor, Currency currency) implements Comparable<Money> {

  public Money {
    Objects.requireNonNull(currency, "currency");
  }

  public static Money of(long amountMinor, String currencyCode) {
    return new Money(amountMinor, Currency.getInstance(currencyCode));
  }

  public static Money of(long amountMinor, Currency currency) {
    return new Money(amountMinor, currency);
  }

  public static Money zero(Currency currency) {
    return new Money(0L, currency);
  }

  /**
   * Converts a decimal amount (e.g. 12.34) to minor units using the currency's default fraction
   * digits.
   */
  public static Money fromDecimal(BigDecimal amount, Currency currency) {
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(currency, "currency");
    BigDecimal scaled =
        amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY);
    return new Money(
        scaled.movePointRight(currency.getDefaultFractionDigits()).longValueExact(), currency);
  }

  public Money plus(Money other) {
    requireSameCurrency(other);
    return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
  }

  public Money minus(Money other) {
    requireSameCurrency(other);
    return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
  }

  public Money negate() {
    return new Money(Math.negateExact(amountMinor), currency);
  }

  public Money abs() {
    return amountMinor < 0 ? negate() : this;
  }

  public boolean isZero() {
    return amountMinor == 0;
  }

  public boolean isPositive() {
    return amountMinor > 0;
  }

  public boolean isNegative() {
    return amountMinor < 0;
  }

  public BigDecimal toDecimal() {
    return BigDecimal.valueOf(amountMinor).movePointLeft(currency.getDefaultFractionDigits());
  }

  public String currencyCode() {
    return currency.getCurrencyCode();
  }

  @Override
  public int compareTo(Money other) {
    requireSameCurrency(other);
    return Long.compare(amountMinor, other.amountMinor);
  }

  private void requireSameCurrency(Money other) {
    Objects.requireNonNull(other, "other");
    if (!currency.equals(other.currency)) {
      throw new CurrencyMismatchException(currency, other.currency);
    }
  }

  @Override
  public String toString() {
    return toDecimal().toPlainString() + " " + currency.getCurrencyCode();
  }
}
