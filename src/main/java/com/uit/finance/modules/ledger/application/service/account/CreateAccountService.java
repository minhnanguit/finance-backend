package com.uit.finance.modules.ledger.application.service.account;

import com.uit.finance.modules.ledger.application.port.in.account.AccountView;
import com.uit.finance.modules.ledger.application.port.in.account.CreateAccountUseCase;
import com.uit.finance.modules.ledger.application.port.out.account.AccountUsagePort;
import com.uit.finance.modules.ledger.application.port.out.account.LoadAccountPort;
import com.uit.finance.modules.ledger.application.port.out.account.SaveAccountPort;
import com.uit.finance.modules.ledger.application.service.shared.CreateOnce;
import com.uit.finance.modules.ledger.application.service.shared.LedgerCommandMapper;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.modules.ledger.domain.model.account.Account;
import com.uit.finance.modules.ledger.domain.model.account.AccountDetails;
import com.uit.finance.modules.ledger.domain.model.account.AccountId;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerLimits;
import com.uit.finance.shared.kernel.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class CreateAccountService implements CreateAccountUseCase {

  private final LoadAccountPort loadAccount;
  private final SaveAccountPort saveAccount;
  private final AccountUsagePort usage;

  CreateAccountService(
      LoadAccountPort loadAccount, SaveAccountPort saveAccount, AccountUsagePort usage) {
    this.loadAccount = loadAccount;
    this.saveAccount = saveAccount;
    this.usage = usage;
  }

  @Override
  public AccountView create(CreateAccountCommand command) {
    UserId owner = command.userId();
    AccountId id = AccountId.of(command.accountId());
    AccountDetails details = LedgerCommandMapper.toDetails(command.fields());

    Account account =
        CreateOnce.run(
            () -> loadAccount.find(owner, id),
            () -> {
              LedgerLimits.ensureCanAddAccount(usage.countAccounts(owner));
              return Account.open(id, owner, details);
            },
            created -> saveAccount.insertIfAbsent(owner, created),
            () -> new LedgerNotFoundException(LedgerEntity.ACCOUNT, id.value()));
    account.requireNotDeleted();
    return AccountView.from(account);
  }
}
