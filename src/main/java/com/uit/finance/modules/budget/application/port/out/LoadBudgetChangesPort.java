package com.uit.finance.modules.budget.application.port.out;

import com.uit.finance.modules.budget.domain.model.Budget;
import com.uit.finance.modules.budget.domain.model.BudgetId;
import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.Optional;

/** Đọc cho sync: bản ghi kèm {@code change_seq}, kể cả tombstone. */
public interface LoadBudgetChangesPort {

  /** Tối đa {@code limit} bản ghi có {@code change_seq > afterSeq}, tăng dần. */
  List<Sequenced<Budget>> changesSince(UserId owner, long afterSeq, int limit);

  /** Id của user khác thì rỗng. */
  Optional<Sequenced<Budget>> findSequenced(UserId owner, BudgetId id);
}
