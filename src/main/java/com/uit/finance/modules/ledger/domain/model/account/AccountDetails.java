package com.uit.finance.modules.ledger.domain.model.account;

import static com.uit.finance.modules.ledger.domain.model.shared.LedgerLimits.MAX_AMOUNT_MINOR;

import com.uit.finance.modules.ledger.domain.model.shared.Fields;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerName;
import com.uit.finance.shared.kernel.Ensure;
import com.uit.finance.shared.kernel.Money;
import java.util.Currency;
import org.jspecify.annotations.Nullable;

/**
 * Phần client được sửa của một ví. Tiền tệ nằm trong {@code openingBalance} nên hai thứ không lệch
 * nhau được. Số dư ban đầu có dấu, {@code |x| ≤ 10¹⁵} (ADR-005 §2).
 */
public record AccountDetails(
    LedgerName name, AccountType type, Money openingBalance, int sortOrder) {

  public AccountDetails {
    Ensure.notNull(name, "name");
    Ensure.notNull(type, "type");
    Ensure.notNull(openingBalance, "openingBalance");
    Fields.between(
        openingBalance.amountMinor(), -MAX_AMOUNT_MINOR, MAX_AMOUNT_MINOR, "openingBalanceMinor");
  }

  public static AccountDetails parse(
      @Nullable String name,
      @Nullable String type,
      @Nullable String currency,
      long openingBalanceMinor,
      int sortOrder) {
    return new AccountDetails(
        new LedgerName(name),
        AccountType.parse(type),
        Money.of(openingBalanceMinor, Fields.currency(currency, "currency")),
        sortOrder);
  }

  public Currency currency() {
    return openingBalance.currency();
  }

  /** Không in tên và số dư (ADR-006 B8). */
  @Override
  public String toString() {
    return "AccountDetails[type=" + type + ", currency=" + currency().getCurrencyCode() + "]";
  }
}
