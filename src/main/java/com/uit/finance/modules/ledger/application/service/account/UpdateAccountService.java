package com.uit.finance.modules.ledger.application.service.account;

import com.uit.finance.modules.ledger.application.port.in.account.AccountView;
import com.uit.finance.modules.ledger.application.port.in.account.UpdateAccountUseCase;
import com.uit.finance.modules.ledger.application.port.out.account.AccountUsagePort;
import com.uit.finance.modules.ledger.application.port.out.account.LoadAccountPort;
import com.uit.finance.modules.ledger.application.port.out.account.SaveAccountPort;
import com.uit.finance.modules.ledger.application.service.shared.LedgerCommandMapper;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.modules.ledger.domain.model.account.Account;
import com.uit.finance.modules.ledger.domain.model.account.AccountId;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.shared.kernel.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class UpdateAccountService implements UpdateAccountUseCase {

  private final LoadAccountPort loadAccount;
  private final SaveAccountPort saveAccount;
  private final AccountUsagePort usage;

  UpdateAccountService(
      LoadAccountPort loadAccount, SaveAccountPort saveAccount, AccountUsagePort usage) {
    this.loadAccount = loadAccount;
    this.saveAccount = saveAccount;
    this.usage = usage;
  }

  @Override
  public AccountView update(UpdateAccountCommand command) {
    UserId owner = command.userId();
    AccountId id = AccountId.of(command.accountId());
    Account account =
        loadAccount
            .find(owner, id)
            .orElseThrow(() -> new LedgerNotFoundException(LedgerEntity.ACCOUNT, id.value()));

    if (account.revise(
        LedgerCommandMapper.toDetails(command.fields()), () -> usage.isReferenced(owner, id))) {
      saveAccount.update(owner, account);
    }
    return AccountView.from(account);
  }
}
