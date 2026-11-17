package com.uit.finance.modules.ledger.application.service.account;

import com.uit.finance.modules.ledger.application.port.in.account.AccountChangeFeedUseCase;
import com.uit.finance.modules.ledger.application.port.in.account.AccountView;
import com.uit.finance.modules.ledger.application.port.out.account.LoadAccountChangesPort;
import com.uit.finance.modules.ledger.application.port.out.shared.Sequenced;
import com.uit.finance.modules.ledger.domain.model.account.Account;
import com.uit.finance.modules.ledger.domain.model.account.AccountId;
import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class AccountChangeFeedService implements AccountChangeFeedUseCase {

  private final LoadAccountChangesPort changes;

  AccountChangeFeedService(LoadAccountChangesPort changes) {
    this.changes = changes;
  }

  @Override
  public List<AccountChange> since(UserId owner, long afterSeq, int limit) {
    return changes.changesSince(owner, afterSeq, limit).stream()
        .map(AccountChangeFeedService::toChange)
        .toList();
  }

  @Override
  public Optional<AccountChange> find(UserId owner, UUID accountId) {
    return changes
        .findSequenced(owner, AccountId.of(accountId))
        .map(AccountChangeFeedService::toChange);
  }

  private static AccountChange toChange(Sequenced<Account> sequenced) {
    return new AccountChange(AccountView.from(sequenced.value()), sequenced.changeSeq());
  }
}
