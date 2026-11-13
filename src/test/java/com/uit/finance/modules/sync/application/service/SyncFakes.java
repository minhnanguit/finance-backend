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
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

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

    /** Chủ có tombstone quá hạn, theo thứ tự tăng dần; mỗi lần dọn trả số lớn nhất đã xoá. */
    final List<UserId> tombstoneOwners = new ArrayList<>();

    final Map<UserId, Long> purgeResults = new HashMap<>();
    final List<UserId> purged = new ArrayList<>();
    final Set<UserId> failingOwners = new HashSet<>();

    final List<UserId> afterOwnersAsked = new ArrayList<>();

    @Override
    public List<UserId> ownersWithTombstonesBefore(
        Instant cutoff, @Nullable UserId afterOwner, int limit) {
      afterOwnersAsked.add(afterOwner);
      int start = afterOwner == null ? 0 : tombstoneOwners.indexOf(afterOwner) + 1;
      return tombstoneOwners.subList(start, tombstoneOwners.size()).stream().limit(limit).toList();
    }

    @Override
    public OptionalLong purgeTombstones(UserId owner, Instant cutoff) {
      if (failingOwners.contains(owner)) {
        throw new IllegalStateException("simulated failure");
      }
      purged.add(owner);
      Long seq = purgeResults.get(owner);
      return seq == null ? OptionalLong.empty() : OptionalLong.of(seq);
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
    public int deleteAllOf(UserId owner) {
      int before = entries.size();
      entries.clear();
      return before;
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

    final Map<UserId, Long> purgedThrough = new HashMap<>();
    final Set<UserId> deleted = new HashSet<>();

    @Override
    public long purgedThroughSeq(UserId owner) {
      return purgedThrough.getOrDefault(owner, 0L);
    }

    @Override
    public void recordPurged(UserId owner, long throughSeq) {
      purgedThrough.merge(owner, throughSeq, Math::max);
    }

    @Override
    public void deleteState(UserId owner) {
      deleted.add(owner);
      initialized.remove(owner);
    }
  }
}
