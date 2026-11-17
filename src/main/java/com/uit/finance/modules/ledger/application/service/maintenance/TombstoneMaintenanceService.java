package com.uit.finance.modules.ledger.application.service.maintenance;

import com.uit.finance.modules.ledger.application.port.in.maintenance.TombstoneMaintenanceUseCase;
import com.uit.finance.modules.ledger.application.port.out.maintenance.TombstonePort;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
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
class TombstoneMaintenanceService implements TombstoneMaintenanceUseCase {

  private final TombstonePort tombstones;

  TombstoneMaintenanceService(TombstonePort tombstones) {
    this.tombstones = tombstones;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserId> ownersWithTombstones(
      LedgerEntity entity, Instant deletedBefore, @Nullable UserId afterOwner, int limit) {
    return tombstones.ownersWithTombstonesBefore(entity, deletedBefore, afterOwner, limit);
  }

  @Override
  public OptionalLong purgeTombstones(LedgerEntity entity, UserId owner, Instant deletedBefore) {
    return tombstones.purgeTombstones(entity, owner, deletedBefore);
  }
}
