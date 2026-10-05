package com.uit.finance.modules.ledger.application.port.in.account;

import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Nguồn thay đổi cho sync pull và cho kết quả từng op (ADR-002 §4). Có cả tombstone. */
public interface AccountChangeFeedUseCase {

  List<AccountChange> since(UserId owner, long afterSeq, int limit);

  /** Bản hiện tại của chính {@code owner}; id của user khác thì rỗng (ADR-006 B2). */
  Optional<AccountChange> find(UserId owner, UUID accountId);

  record AccountChange(AccountView account, long changeSeq) {}
}
