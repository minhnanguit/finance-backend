package com.uit.finance.modules.ledger.application.service;

import com.uit.finance.modules.ledger.application.port.in.AccountView;
import com.uit.finance.modules.ledger.application.port.in.ArchiveAccountUseCase;
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
class ArchiveAccountService implements ArchiveAccountUseCase {

  private final LoadAccountPort loadAccount;
  private final SaveAccountPort saveAccount;
  private final Clock clock;

  ArchiveAccountService(LoadAccountPort loadAccount, SaveAccountPort saveAccount, Clock clock) {
    this.loadAccount = loadAccount;
    this.saveAccount = saveAccount;
    this.clock = clock;
  }

  @Override
  public AccountView setArchived(ArchiveAccountCommand command) {
    UserId owner = command.userId();
    AccountId id = AccountId.of(command.accountId());
    Account account =
        loadAccount
            .find(owner, id)
            .orElseThrow(() -> new LedgerNotFoundException(LedgerEntity.ACCOUNT, id.value()));

    if (account.changeArchived(command.archived(), clock.instant())) {
      saveAccount.update(owner, account);
    }
    return AccountView.from(account);
  }
}
