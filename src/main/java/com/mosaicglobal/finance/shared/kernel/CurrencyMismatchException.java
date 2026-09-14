package com.mosaicglobal.finance.shared.kernel;

import java.util.Currency;

/** Raised when arithmetic is attempted between two different currencies. */
public class CurrencyMismatchException extends DomainException {

  public CurrencyMismatchException(Currency left, Currency right) {
    super(
        ErrorCategory.BUSINESS_RULE,
        "money.currency_mismatch",
        "Cannot operate on %s and %s".formatted(left.getCurrencyCode(), right.getCurrencyCode()));
  }
}
