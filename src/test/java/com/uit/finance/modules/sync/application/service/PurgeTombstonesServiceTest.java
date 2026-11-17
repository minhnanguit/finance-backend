package com.uit.finance.modules.sync.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Điều phối dọn tombstone với fake; SQL thật và FK được phủ ở {@code TombstonePurgeIT}. */
class PurgeTombstonesServiceTest {

  private static final Instant CUTOFF = Instant.parse("2026-08-01T00:00:00Z");

  private SyncFakes.Handlers handlers;
  private SyncFakes.State state;
  private PurgeTombstonesService service;

  @BeforeEach
  void setUp() {
    handlers = new SyncFakes.Handlers();
    state = new SyncFakes.State();
    service = new PurgeTombstonesService(handlers, new OwnerTombstonePurgeService(state, handlers));
  }

  @Test
  @DisplayName("mỗi user: khoá ghi trước, dọn, rồi nâng mốc tới số lớn nhất đã xoá")
  void purgesEachOwnerUnderItsWriteLock() {
    UserId ann = UserId.newId();
    UserId bob = UserId.newId();
    handlers.tombstoneOwners.addAll(java.util.List.of(ann, bob));
    handlers.purgeResults.put(ann, 7L);

    int owners = service.purgeDeletedBefore(CUTOFF);

    assertThat(owners).isEqualTo(2);
    assertThat(state.writeLocks).containsExactly(ann, bob);
    assertThat(handlers.purged).containsExactly(ann, bob);
    assertThat(state.purgedThroughSeq(ann)).isEqualTo(7);
    // Không xoá được gì (tombstone còn bị trỏ tới) thì mốc giữ nguyên.
    assertThat(state.purgedThroughSeq(bob)).isZero();
  }

  @Test
  @DisplayName("mốc chỉ tăng: lần dọn sau với số nhỏ hơn không hạ mốc")
  void markerNeverGoesDown() {
    UserId ann = UserId.newId();
    handlers.tombstoneOwners.add(ann);
    state.recordPurged(ann, 40);
    handlers.purgeResults.put(ann, 12L);

    service.purgeDeletedBefore(CUTOFF);

    assertThat(state.purgedThroughSeq(ann)).isEqualTo(40);
  }

  @Test
  @DisplayName("một user lỗi không chặn các user sau")
  void oneFailingOwnerDoesNotStopTheRun() {
    UserId ann = UserId.newId();
    UserId bob = UserId.newId();
    UserId cid = UserId.newId();
    handlers.tombstoneOwners.addAll(java.util.List.of(ann, bob, cid));
    handlers.failingOwners.add(bob);

    int owners = service.purgeDeletedBefore(CUTOFF);

    assertThat(owners).isEqualTo(2);
    assertThat(handlers.purged).containsExactly(ann, cid);
  }

  @Test
  @DisplayName("đi theo keyset: hết một lô thì hỏi tiếp từ user cuối của lô, mỗi user đúng một lần")
  void walksOwnersPageByPage() {
    var owners =
        IntStream.rangeClosed(1, PurgeTombstonesService.OWNER_BATCH + 1)
            .mapToObj(i -> UserId.newId())
            .toList();
    handlers.tombstoneOwners.addAll(owners);

    int purged = service.purgeDeletedBefore(CUTOFF);

    assertThat(purged).isEqualTo(owners.size());
    assertThat(handlers.purged).containsExactlyElementsOf(owners);
    assertThat(handlers.afterOwnersAsked)
        .containsExactly(null, owners.get(PurgeTombstonesService.OWNER_BATCH - 1));
  }
}
