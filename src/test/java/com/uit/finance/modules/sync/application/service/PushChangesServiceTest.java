package com.uit.finance.modules.sync.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.finance.modules.sync.application.port.in.PushChangesUseCase.PushCommand;
import com.uit.finance.modules.sync.domain.exception.InvalidBatchException;
import com.uit.finance.modules.sync.domain.model.OpAction;
import com.uit.finance.modules.sync.domain.model.OpOutcome;
import com.uit.finance.modules.sync.domain.model.OpResult;
import com.uit.finance.modules.sync.domain.model.OpVerdict;
import com.uit.finance.modules.sync.domain.model.PushedOp;
import com.uit.finance.shared.kernel.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Điều phối push với fake. Rollback thật của transaction được phủ ở {@code SyncApiIT}; ở đây kiểm
 * luồng: op nào được ghi nhật ký, ở bước nào, kết quả gì.
 */
class PushChangesServiceTest {

  private static final UserId ANN = UserId.newId();
  private static final Instant NOW = Instant.parse("2026-10-05T08:00:00Z");

  private SyncFakes.Handlers handlers;
  private SyncFakes.OpLog opLog;
  private SyncFakes.State state;
  private PushChangesService service;

  @BeforeEach
  void setUp() {
    handlers = new SyncFakes.Handlers();
    opLog = new SyncFakes.OpLog();
    state = new SyncFakes.State();
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    service =
        new PushChangesService(
            new UserSyncInitializer(state, handlers, clock),
            new ApplyOpService(state, opLog, handlers, clock),
            new RecordOpOutcomeService(opLog, handlers, clock));
  }

  private static PushedOp op(String entity) {
    return new PushedOp(
        UUID.randomUUID(), entity, UUID.randomUUID(), OpAction.UPSERT, Map.of("name", "Ví"));
  }

  private List<OpResult> push(PushedOp... ops) {
    return service.push(new PushCommand(ANN, "device-1", List.of(ops)));
  }

  @Test
  @DisplayName("op được áp: APPLIED, ghi nhật ký kèm máy và thời điểm, trả bản hiện tại")
  void appliedOpIsLogged() {
    PushedOp op = op("account");

    OpResult result = push(op).getFirst();

    assertThat(result.outcome()).isEqualTo(OpOutcome.APPLIED);
    assertThat(result.current()).isNotNull();
    assertThat(opLog.entries.get(op.opId()).deviceId().value()).isEqualTo("device-1");
    assertThat(opLog.entries.get(op.opId()).processedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName(
      "mỗi op khoá dòng của user trước khi áp, để luật kiểm-rồi-ghi không bị request song song lách")
  void everyOpTakesTheUserWriteLock() {
    push(op("account"), op("account"));

    assertThat(state.writeLocks).containsExactly(ANN, ANN);
  }

  @Test
  @DisplayName("gửi lại cùng opId: DUPLICATE, handler không được gọi lần hai")
  void sameOpIdIsDuplicate() {
    PushedOp op = op("account");
    push(op);

    OpResult again = push(op).getFirst();

    assertThat(again.outcome()).isEqualTo(OpOutcome.DUPLICATE);
    assertThat(handlers.applied).containsExactly(op.opId());
  }

  @Test
  @DisplayName("REJECTED được ghi nhật ký với mã lỗi; gửi lại cũng là DUPLICATE")
  void rejectedIsFinal() {
    handlers.verdicts = op -> new OpVerdict(OpOutcome.REJECTED, "ledger.archived");
    PushedOp op = op("account");

    OpResult first = push(op).getFirst();
    OpResult again = push(op).getFirst();

    assertThat(first.outcome()).isEqualTo(OpOutcome.REJECTED);
    assertThat(first.code()).isEqualTo("ledger.archived");
    assertThat(opLog.entries.get(op.opId()).code()).isEqualTo("ledger.archived");
    assertThat(again.outcome()).isEqualTo(OpOutcome.DUPLICATE);
  }

  @Test
  @DisplayName("RETRY không chốt: gửi lại sau đó được áp bình thường")
  void retryIsNotFinal() {
    handlers.verdicts = op -> new OpVerdict(OpOutcome.RETRY, "ledger.reference_pending");
    PushedOp op = op("account");
    push(op);
    handlers.verdicts = op2 -> OpVerdict.applied();

    OpResult later = push(op).getFirst();

    assertThat(later.outcome()).isEqualTo(OpOutcome.APPLIED);
    assertThat(opLog.entries.get(op.opId()).outcome()).isEqualTo(OpOutcome.APPLIED);
  }

  @Test
  @DisplayName("entity lạ: REJECTED sync.unknown_entity, current null")
  void unknownEntity() {
    OpResult result = push(op("budget")).getFirst();

    assertThat(result.outcome()).isEqualTo(OpOutcome.REJECTED);
    assertThat(result.code()).isEqualTo(OpVerdict.UNKNOWN_ENTITY);
    assertThat(result.current()).isNull();
  }

  @Test
  @DisplayName("lỗi bất ngờ ở một op: op đó RETRY sync.server_error, op sau vẫn chạy")
  void unexpectedFailureDoesNotSpoilTheBatch() {
    PushedOp broken = op("account");
    handlers.verdicts =
        op -> {
          if (op.opId().equals(broken.opId())) {
            throw new IllegalStateException("boom");
          }
          return OpVerdict.applied();
        };

    List<OpResult> results = push(broken, op("account"));

    assertThat(results)
        .extracting(OpResult::outcome)
        .containsExactly(OpOutcome.RETRY, OpOutcome.APPLIED);
    assertThat(results.getFirst().code()).isEqualTo(OpVerdict.SERVER_ERROR);
  }

  @Test
  @DisplayName("khởi tạo user đúng một lần, ở lần push đầu tiên")
  void initializesOnce() {
    push(op("account"));
    push(op("account"));

    assertThat(handlers.initializations).isEqualTo(1);
  }

  @Test
  @DisplayName("batch rỗng, quá 100 op, deviceId sai: cả batch bị từ chối")
  void invalidBatches() {
    List<PushedOp> tooMany = Collections.nCopies(101, op("account"));

    assertThatThrownBy(() -> service.push(new PushCommand(ANN, "device-1", List.of())))
        .isInstanceOf(InvalidBatchException.class);
    assertThatThrownBy(() -> service.push(new PushCommand(ANN, "device-1", tooMany)))
        .isInstanceOf(InvalidBatchException.class);
    assertThatThrownBy(
            () -> service.push(new PushCommand(ANN, "bad device!", List.of(op("account")))))
        .isInstanceOf(InvalidBatchException.class);
  }

  @Test
  @DisplayName("dọn nhật ký theo lô tới khi hết dòng quá hạn")
  void purgeLoopsUntilDone() {
    for (int i = 0; i < PurgeOpLogService.BATCH_SIZE + 3; i++) {
      PushedOp op = op("account");
      opLog.record(
          ANN,
          com.uit.finance.modules.sync.domain.model.OpLogEntry.of(
              op,
              com.uit.finance.modules.sync.domain.model.DeviceId.of("d"),
              OpVerdict.applied(),
              NOW.minusSeconds(60)));
    }

    int deleted = new PurgeOpLogService(opLog).purgeProcessedBefore(NOW);

    assertThat(deleted).isEqualTo(PurgeOpLogService.BATCH_SIZE + 3);
    assertThat(opLog.entries).isEmpty();
  }
}
