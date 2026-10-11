package com.uit.finance.modules.sync.application.service;

import com.uit.finance.modules.sync.application.port.out.OpLogPort;
import com.uit.finance.modules.sync.application.port.out.SyncHandlersPort;
import com.uit.finance.modules.sync.application.port.out.SyncStatePort;
import com.uit.finance.modules.sync.domain.model.ChangeRecord;
import com.uit.finance.modules.sync.domain.model.OpLogEntry;
import com.uit.finance.modules.sync.domain.model.OpOutcome;
import com.uit.finance.modules.sync.domain.model.OpVerdict;
import com.uit.finance.modules.sync.domain.model.PushedOp;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Fake in-memory cho port của sync. */
final class SyncFakes {

  private SyncFakes() {}

  static final class Handlers implements SyncHandlersPort {

    /** Kết quả cho từng op, mặc định APPLIED. */
    Function<PushedOp, OpVerdict> verdicts = op -> OpVerdict.applied();

    final List<UUID> applied = new ArrayList<>();
    int initializations;

    @Override
    public boolean supports(String entity) {
      return entity.equals("account");
    }

    @Override
    public OpVerdict apply(UserId owner, PushedOp op) {
      OpVerdict verdict = verdicts.apply(op);
      if (verdict.outcome() == OpOutcome.APPLIED) {
        applied.add(op.opId());
      }
      return verdict;
    }

    @Override
    public Optional<ChangeRecord> current(UserId owner, String entity, UUID id) {
      return supports(entity)
          ? Optional.of(new ChangeRecord(entity, id, 1, false, Map.of("name", "Ví")))
          : Optional.empty();
    }

    @Override
    public List<ChangeRecord> changesSince(UserId owner, long afterSeq, int limit) {
      return List.of();
    }

    @Override
    public void initialize(UserId owner) {
      initializations++;
    }
  }

  static final class OpLog implements OpLogPort {

    final Map<UUID, OpLogEntry> entries = new HashMap<>();

    @Override
    public Optional<OpOutcome> findOutcome(UserId owner, UUID opId) {
      return Optional.ofNullable(entries.get(opId)).map(OpLogEntry::outcome);
    }

    @Override
    public boolean record(UserId owner, OpLogEntry entry) {
      OpLogEntry existing = entries.get(entry.opId());
      if (existing != null && existing.outcome().isFinal()) {
        return false;
      }
      entries.put(entry.opId(), entry);
      return true;
    }

    @Override
    public int purgeProcessedBefore(Instant cutoff, int batchSize) {
      List<UUID> expired =
          entries.values().stream()
              .filter(entry -> entry.processedAt().isBefore(cutoff))
              .limit(batchSize)
              .map(OpLogEntry::opId)
              .toList();
      expired.forEach(entries::remove);
      return expired.size();
    }
  }

  static final class State implements SyncStatePort {

    final Set<UserId> initialized = new HashSet<>();
    final List<UserId> writeLocks = new ArrayList<>();

    @Override
    public void lockWrites(UserId owner) {
      writeLocks.add(owner);
    }

    @Override
    public boolean isInitialized(UserId owner) {
      return initialized.contains(owner);
    }

    @Override
    public boolean lockForInitialization(UserId owner) {
      return !initialized.contains(owner);
    }

    @Override
    public void markInitialized(UserId owner, Instant at) {
      initialized.add(owner);
    }

    @Override
    public long lastIssuedSeq(UserId owner) {
      return 0;
    }
  }
}
