package com.uit.finance.modules.ledger.application.port.out.account;

import com.uit.finance.modules.ledger.application.port.out.shared.Sequenced;
import com.uit.finance.modules.ledger.domain.model.account.Account;
import com.uit.finance.modules.ledger.domain.model.account.AccountId;
import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.Optional;

/** Đọc cho sync: bản ghi kèm {@code change_seq}, kể cả tombstone. */
public interface LoadAccountChangesPort {

  /** Tối đa {@code limit} bản ghi có {@code change_seq > afterSeq}, tăng dần. */
  List<Sequenced<Account>> changesSince(UserId owner, long afterSeq, int limit);

  /** Id của user khác thì rỗng. */
  Optional<Sequenced<Account>> findSequenced(UserId owner, AccountId id);
}
