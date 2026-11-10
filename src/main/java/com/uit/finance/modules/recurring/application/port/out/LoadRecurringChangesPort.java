package com.uit.finance.modules.recurring.application.port.out;

import com.uit.finance.modules.recurring.domain.model.Recurring;
import com.uit.finance.modules.recurring.domain.model.RecurringId;
import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.Optional;

/** Đọc cho sync: bản ghi kèm {@code change_seq}, kể cả tombstone. */
public interface LoadRecurringChangesPort {

  /** Tối đa {@code limit} bản ghi có {@code change_seq > afterSeq}, tăng dần. */
  List<Sequenced<Recurring>> changesSince(UserId owner, long afterSeq, int limit);

  /** Id của user khác thì rỗng. */
  Optional<Sequenced<Recurring>> findSequenced(UserId owner, RecurringId id);
}
