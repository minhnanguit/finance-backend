package com.uit.finance.modules.ledger.domain.model;

import com.uit.finance.shared.kernel.Money;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Số dư từng ví và tổng theo từng tiền tệ (ADR-005 §5).
 *
 * <p>Tổng không cộng thẳng các tiền tệ khác nhau (ADR-001) và bỏ qua ví đã archive, vì ví archive
 * đã bị ẩn khỏi danh sách.
 */
public record BalanceSheet(List<AccountBalance> accounts, List<Money> totals) {

  public BalanceSheet {
    accounts = List.copyOf(accounts);
    totals = List.copyOf(totals);
  }

  /**
   * @param flows theo ví; ví chưa có giao dịch nào thì không cần có mặt
   */
  public static BalanceSheet of(List<Account> accounts, Map<AccountId, AccountFlows> flows) {
    List<AccountBalance> balances =
        accounts.stream()
            .filter(account -> !account.isDeleted())
            .map(
                account ->
                    new AccountBalance(
                        account.getId(),
                        account.balance(
                            flows.getOrDefault(
                                account.getId(), AccountFlows.none(account.getId()))),
                        account.isArchived()))
            .toList();

    Map<Currency, Money> totals = new TreeMap<>(Comparator.comparing(Currency::getCurrencyCode));
    balances.stream()
        .filter(balance -> !balance.archived())
        .forEach(
            balance -> totals.merge(balance.balance().currency(), balance.balance(), Money::plus));
    return new BalanceSheet(balances, List.copyOf(totals.values()));
  }

  public record AccountBalance(AccountId accountId, Money balance, boolean archived) {}
}
