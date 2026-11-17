package com.uit.finance.modules.budget.application.service;

import com.uit.finance.modules.budget.application.port.in.PurgeBudgetTombstonesUseCase;
import com.uit.finance.modules.budget.application.port.out.BudgetTombstonePort;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chạy trong transaction do sync mở (đã khoá dãy số của user), {@code REQUIRED} nên join vào đó.
 */
@Service
@Transactional
class PurgeBudgetTombstonesService implements PurgeBudgetTombstonesUseCase {

  private final BudgetTombstonePort tombstones;

  PurgeBudgetTombstonesService(BudgetTombstonePort tombstones) {
    this.tombstones = tombstones;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserId> ownersWithTombstones(
      Instant deletedBefore, @Nullable UserId afterOwner, int limit) {
    return tombstones.ownersWithTombstonesBefore(deletedBefore, afterOwner, limit);
  }

  @Override
  public OptionalLong purgeTombstones(UserId owner, Instant deletedBefore) {
    return tombstones.purgeTombstones(owner, deletedBefore);
  }
}
