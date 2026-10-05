package com.uit.finance.modules.sync.adapter.out.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.finance.modules.sync.domain.model.ChangeRecord;
import com.uit.finance.shared.kernel.UserId;
import com.uit.finance.shared.sync.SyncChange;
import com.uit.finance.shared.sync.SyncHandler;
import com.uit.finance.shared.sync.SyncOp;
import com.uit.finance.shared.sync.SyncOpResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.LongStream;
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

    assertThat(registry.supports("budget")).isFalse();
    assertThat(registry.current(ANN, "budget", UUID.randomUUID())).isEmpty();
  }
}
