package com.uit.finance.modules.ledger.application.port.out;

import com.uit.finance.modules.ledger.domain.model.Account;
import com.uit.finance.modules.ledger.domain.model.AccountId;
import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.Optional;

public interface LoadAccountPort {

  /** Trả cả bản đã xoá (tombstone). Id của user khác thì trả rỗng. */
  Optional<Account> find(UserId owner, AccountId id);

  /** Ví chưa xoá của user, kể cả đã archive, theo {@code sortOrder} rồi {@code id}. */
  List<Account> listAccounts(UserId owner);
}
