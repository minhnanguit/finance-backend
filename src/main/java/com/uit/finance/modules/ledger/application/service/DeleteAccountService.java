package com.uit.finance.modules.ledger.application.service;

import com.uit.finance.modules.ledger.application.port.in.AccountView;
import com.uit.finance.modules.ledger.application.port.in.DeleteAccountUseCase;
import com.uit.finance.modules.ledger.application.port.out.AccountUsagePort;
import com.uit.finance.modules.ledger.application.port.out.LoadAccountPort;
import com.uit.finance.modules.ledger.application.port.out.SaveAccountPort;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.modules.ledger.domain.model.Account;
import com.uit.finance.modules.ledger.domain.model.AccountId;
import com.uit.finance.modules.ledger.domain.model.LedgerEntity;
import com.uit.finance.shared.kernel.UserId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class DeleteAccountService implements DeleteAccountUseCase {

  private final LoadAccountPort loadAccount;
  private final SaveAccountPort saveAccount;
  private final AccountUsagePort usage;
  private final Clock clock;

  DeleteAccountService(
      LoadAccountPort loadAccount,
      SaveAccountPort saveAccount,
      AccountUsagePort usage,
      Clock clock) {
    this.loadAccount = loadAccount;
    this.saveAccount = saveAccount;
    this.usage = usage;
    this.clock = clock;
  }

  @Override
  public AccountView delete(DeleteAccountCommand command) {
    UserId owner = command.userId();
    AccountId id = AccountId.of(command.accountId());
    Account account =
        loadAccount
            .find(owner, id)
            .orElseThrow(() -> new LedgerNotFoundException(LedgerEntity.ACCOUNT, id.value()));

    if (account.delete(clock.instant(), () -> usage.isReferenced(owner, id))) {
      saveAccount.update(owner, account);
    }
    return AccountView.from(account);
  }
}
