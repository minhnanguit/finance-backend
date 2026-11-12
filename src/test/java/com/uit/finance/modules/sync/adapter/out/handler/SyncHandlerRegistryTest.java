package com.uit.finance.modules.sync.adapter.out.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.finance.modules.sync.domain.model.ChangeRecord;
import com.uit.finance.shared.kernel.UserId;
import com.uit.finance.shared.sync.SyncChange;
import com.uit.finance.shared.sync.SyncHandler;
import com.uit.finance.shared.sync.SyncOp;
import com.uit.finance.shared.sync.SyncOpResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SyncHandlerRegistryTest {

  private static final UserId ANN = UserId.newId();

  private static SyncHandler handler(String entity, long... seqs) {
    return new SyncHandler() {
      @Override
      public String entity() {
        return entity;
      }

      @Override
      public SyncOpResult apply(UserId owner, SyncOp op) {
        return SyncOpResult.applied();
      }

      @Override
      public Optional<SyncChange> current(UserId owner, UUID id) {
        return Optional.empty();
      }

      @Override
      public List<SyncChange> changesSince(UserId owner, long afterSeq, int limit) {
        return LongStream.of(seqs)
            .filter(seq -> seq > afterSeq)
            .limit(limit)
            .mapToObj(seq -> new SyncChange(entity, UUID.randomUUID(), seq, false, null))
            .toList();
      }

      @Override
      public List<UserId> ownersWithTombstonesBefore(
          Instant cutoff, @Nullable UserId afterOwner, int limit) {
        return List.of();
      }

      @Override
      public OptionalLong purgeTombstones(UserId owner, Instant cutoff) {
        return OptionalLong.empty();
      }
    };
  }

  /**
   * Handler chỉ để thử phần dọn tombstone: danh sách chủ (đã xếp theo thứ tự DB) và số lớn nhất.
   */
  private static SyncHandler tombstones(String entity, OptionalLong purged, UserId... owners) {
    return new SyncHandler() {
      @Override
      public String entity() {
        return entity;
      }

      @Override
      public SyncOpResult apply(UserId owner, SyncOp op) {
        return SyncOpResult.applied();
      }

      @Override
      public Optional<SyncChange> current(UserId owner, UUID id) {
        return Optional.empty();
      }

      @Override
      public List<SyncChange> changesSince(UserId owner, long afterSeq, int limit) {
        return List.of();
      }

      @Override
      public List<UserId> ownersWithTombstonesBefore(
          Instant cutoff, @Nullable UserId afterOwner, int limit) {
        return Stream.of(owners)
            .filter(
                owner ->
                    afterOwner == null
                        || SyncHandlerRegistry.POSTGRES_UUID_ORDER.compare(owner, afterOwner) > 0)
            .limit(limit)
            .toList();
      }

      @Override
      public OptionalLong purgeTombstones(UserId owner, Instant cutoff) {
        return purged;
      }
    };
  }

  @Test
  @DisplayName("thứ tự UUID giống Postgres: so từng byte không dấu, khác UUID.compareTo")
  void uuidOrderMatchesPostgres() {
    UserId low = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    UserId high = new UserId(UUID.fromString("ffffffff-0000-0000-0000-000000000000"));

    // UUID.compareTo coi 0xffffffff... là số âm nên xếp nó trước; Postgres xếp nó sau.
    assertThat(high.value().compareTo(low.value())).isNegative();
    assertThat(SyncHandlerRegistry.POSTGRES_UUID_ORDER.compare(high, low)).isPositive();
  }

  @Test
  @DisplayName("trộn chủ có tombstone của mọi entity: bỏ trùng, đúng thứ tự DB, cắt còn limit")
  void mergesTombstoneOwners() {
    UserId a = new UserId(UUID.fromString("10000000-0000-0000-0000-000000000000"));
    UserId b = new UserId(UUID.fromString("80000000-0000-0000-0000-000000000000"));
    UserId c = new UserId(UUID.fromString("f0000000-0000-0000-0000-000000000000"));
    SyncHandlerRegistry registry =
        new SyncHandlerRegistry(
            List.of(
                tombstones("account", OptionalLong.of(7), a, c),
                tombstones("transaction", OptionalLong.of(12), a, b)));

    assertThat(registry.ownersWithTombstonesBefore(Instant.EPOCH, null, 2)).containsExactly(a, b);
    assertThat(registry.ownersWithTombstonesBefore(Instant.EPOCH, b, 10)).containsExactly(c);
    assertThat(registry.purgeTombstones(a, Instant.EPOCH)).hasValue(12);
  }

  @Test
  @DisplayName(
      "tombstone còn bị entity khác trỏ tới: dọn lặp tới khi hết, không phụ thuộc thứ tự handler")
  void purgesDependentTombstonesInOneRun() {
    for (boolean accountFirst : new boolean[] {true, false}) {
      AtomicBoolean transactionsPurged = new AtomicBoolean();
      AtomicBoolean accountsPurged = new AtomicBoolean();
      // Ví chỉ xoá được khi giao dịch đã xoá trỏ tới nó đi trước (NOT EXISTS ở adapter thật).
      SyncHandler transactions =
          purging(
              "transaction",
              () -> transactionsPurged.getAndSet(true) ? OptionalLong.empty() : OptionalLong.of(9));
      SyncHandler accounts =
          purging(
              "account",
              () ->
                  transactionsPurged.get() && !accountsPurged.getAndSet(true)
                      ? OptionalLong.of(5)
                      : OptionalLong.empty());
      SyncHandlerRegistry registry =
          new SyncHandlerRegistry(
              accountFirst ? List.of(accounts, transactions) : List.of(transactions, accounts));

      assertThat(registry.purgeTombstones(UserId.newId(), Instant.EPOCH)).hasValue(9);
      assertThat(accountsPurged).isTrue();
    }
  }

  private static SyncHandler purging(String entity, Supplier<OptionalLong> purge) {
    SyncHandler base = tombstones(entity, OptionalLong.empty());
    return new SyncHandler() {
      @Override
      public String entity() {
        return entity;
      }

      @Override
      public SyncOpResult apply(UserId owner, SyncOp op) {
        return base.apply(owner, op);
      }

      @Override
      public Optional<SyncChange> current(UserId owner, UUID id) {
        return Optional.empty();
      }

      @Override
      public List<SyncChange> changesSince(UserId owner, long afterSeq, int limit) {
        return List.of();
      }

      @Override
      public List<UserId> ownersWithTombstonesBefore(
          Instant cutoff, @Nullable UserId afterOwner, int limit) {
        return List.of();
      }

      @Override
      public OptionalLong purgeTombstones(UserId owner, Instant cutoff) {
        return purge.get();
      }
    };
  }

  @Test
  @DisplayName("trộn thay đổi của mọi entity theo change_seq rồi cắt còn limit")
  void mergesAcrossEntities() {
    SyncHandlerRegistry registry =
        new SyncHandlerRegistry(List.of(handler("account", 2, 5), handler("transaction", 1, 3, 4)));

    List<ChangeRecord> changes = registry.changesSince(ANN, 0, 4);

    assertThat(changes).extracting(ChangeRecord::changeSeq).containsExactly(1L, 2L, 3L, 4L);
    assertThat(changes)
        .extracting(ChangeRecord::entity)
        .containsExactly("transaction", "account", "transaction", "transaction");
  }

  @Test
  @DisplayName("hai handler trùng entity thì không khởi động được")
  void duplicateEntitiesFailFast() {
    assertThatThrownBy(
            () -> new SyncHandlerRegistry(List.of(handler("account"), handler("account"))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("entity lạ: không hỗ trợ, current rỗng")
  void unknownEntity() {
    SyncHandlerRegistry registry = new SyncHandlerRegistry(List.of(handler("account")));

    assertThat(registry.supports("recurring")).isFalse();
    assertThat(registry.current(ANN, "recurring", UUID.randomUUID())).isEmpty();
  }
}
