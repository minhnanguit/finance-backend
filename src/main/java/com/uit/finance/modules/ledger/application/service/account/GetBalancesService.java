package com.uit.finance.modules.ledger.application.service.account;

import com.uit.finance.modules.ledger.application.port.in.account.GetBalancesUseCase;
import com.uit.finance.modules.ledger.application.port.out.account.LoadAccountFlowsPort;
import com.uit.finance.modules.ledger.application.port.out.account.LoadAccountPort;
import com.uit.finance.modules.ledger.domain.model.account.AccountFlows;
import com.uit.finance.modules.ledger.domain.model.account.AccountId;
import com.uit.finance.modules.ledger.domain.model.account.BalanceSheet;
import com.uit.finance.shared.kernel.UserId;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Hai query cho mọi ví: danh sách ví + một câu SUM gom theo ví. Không load từng giao dịch. */
@Service
@Transactional(readOnly = true)
class GetBalancesService implements GetBalancesUseCase {

  private final LoadAccountPort loadAccount;
  private final LoadAccountFlowsPort loadFlows;

  GetBalancesService(LoadAccountPort loadAccount, LoadAccountFlowsPort loadFlows) {
    this.loadAccount = loadAccount;
    this.loadFlows = loadFlows;
  }

  @Override
  public Balances balances(UserId userId) {
    Map<AccountId, AccountFlows> flows =
        loadFlows.flowsByAccount(userId).stream()
            .collect(Collectors.toMap(AccountFlows::accountId, Function.identity()));
    BalanceSheet sheet = BalanceSheet.of(loadAccount.listAccounts(userId), flows);

    return new Balances(
        sheet.accounts().stream()
            .map(
                balance ->
                    new AccountBalance(
                        balance.accountId().value(),
                        balance.balance().amountMinor(),
                        balance.balance().currencyCode(),
                        balance.archived()))
            .toList(),
        sheet.totals().stream()
            .map(total -> new CurrencyTotal(total.currencyCode(), total.amountMinor()))
            .toList());
  }
}
