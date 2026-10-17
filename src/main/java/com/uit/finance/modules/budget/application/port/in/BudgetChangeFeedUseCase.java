package com.uit.finance.modules.budget.application.port.in;

import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Nguồn thay đổi cho sync pull và cho kết quả từng op (ADR-002 §4). Có cả tombstone. */
public interface BudgetChangeFeedUseCase {

  List<BudgetChange> since(UserId owner, long afterSeq, int limit);

  /** Bản hiện tại của chính {@code owner}; id của user khác thì rỗng (ADR-006 B2). */
  Optional<BudgetChange> find(UserId owner, UUID budgetId);

  record BudgetChange(BudgetView budget, long changeSeq) {}
}
