package com.uit.finance.modules.recurring.application.port.in;

import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Nguồn thay đổi cho sync pull và cho kết quả từng op (ADR-002 §4). Có cả tombstone. */
public interface RecurringChangeFeedUseCase {

  List<RecurringChange> since(UserId owner, long afterSeq, int limit);

  /** Bản hiện tại của chính {@code owner}; id của user khác thì rỗng (ADR-006 B2). */
  Optional<RecurringChange> find(UserId owner, UUID recurringId);

  record RecurringChange(RecurringView recurring, long changeSeq) {}
}
