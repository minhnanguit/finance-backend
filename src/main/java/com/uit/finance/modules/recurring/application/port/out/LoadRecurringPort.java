package com.uit.finance.modules.recurring.application.port.out;

import com.uit.finance.modules.recurring.domain.model.Recurring;
import com.uit.finance.modules.recurring.domain.model.RecurringId;
import com.uit.finance.shared.kernel.UserId;
import java.util.Optional;

public interface LoadRecurringPort {

  /** Trả cả bản đã xoá (tombstone), kèm timeline. Id của user khác thì rỗng. */
  Optional<Recurring> find(UserId owner, RecurringId id);
}
