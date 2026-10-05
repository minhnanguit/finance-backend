package com.uit.finance.modules.sync.application.service;

import com.uit.finance.modules.sync.application.port.in.PushChangesUseCase;
import com.uit.finance.modules.sync.domain.exception.InvalidBatchException;
import com.uit.finance.modules.sync.domain.model.DeviceId;
import com.uit.finance.modules.sync.domain.model.OpOutcome;
import com.uit.finance.modules.sync.domain.model.OpResult;
import com.uit.finance.modules.sync.domain.model.OpVerdict;
import com.uit.finance.modules.sync.domain.model.PushedOp;
import com.uit.finance.modules.sync.domain.model.SyncLimits;
import com.uit.finance.shared.kernel.UserId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Điều phối một batch push. Không mở transaction: mỗi op có transaction riêng ở {@link
 * ApplyOpService}, nên một op lỗi không làm hỏng op khác (ADR-002 §7).
 *
 * <p>Log chỉ có id, số lượng, kết quả (ADR-006 B8). Lỗi bất ngờ chỉ log tên class: message của lỗi
 * SQL có thể chứa nguyên dòng dữ liệu, kể cả ghi chú và số tiền.
 */
@Service
class PushChangesService implements PushChangesUseCase {

  private static final Logger log = LoggerFactory.getLogger(PushChangesService.class);

  private final UserSyncInitializer initializer;
  private final ApplyOpService applyOp;
  private final RecordOpOutcomeService recordOutcome;

  PushChangesService(
      UserSyncInitializer initializer,
      ApplyOpService applyOp,
      RecordOpOutcomeService recordOutcome) {
    this.initializer = initializer;
    this.applyOp = applyOp;
    this.recordOutcome = recordOutcome;
  }

  @Override
  public List<OpResult> push(PushCommand command) {
    UserId owner = command.userId();
    DeviceId device = DeviceId.of(command.deviceId());
    if (command.ops().isEmpty() || command.ops().size() > SyncLimits.MAX_OPS_PER_PUSH) {
      throw new InvalidBatchException(
          "a push carries 1 to " + SyncLimits.MAX_OPS_PER_PUSH + " operations");
    }

    initializer.ensureInitialized(owner);
    List<OpResult> results = command.ops().stream().map(op -> process(owner, device, op)).toList();
    log.info(
        "sync push user={} device={} ops={} outcomes={}",
        owner,
        device.value(),
        results.size(),
        countByOutcome(results));
    return results;
  }

  private OpResult process(UserId owner, DeviceId device, PushedOp op) {
    try {
      return applyOp.apply(owner, device, op);
    } catch (OpNotAppliedException notApplied) {
      log.debug(
          "sync op={} entity={} outcome={} code={}",
          op.opId(),
          op.entity(),
          notApplied.verdict().outcome(),
          notApplied.verdict().code());
      return recordOutcome.record(owner, device, op, notApplied.verdict());
    } catch (RuntimeException unexpected) {
      log.error(
          "sync op={} entity={} failed with {}",
          op.opId(),
          op.entity(),
          unexpected.getClass().getName());
      return recordOutcome.record(owner, device, op, OpVerdict.serverError());
    }
  }

  private static Map<OpOutcome, Long> countByOutcome(List<OpResult> results) {
    Map<OpOutcome, Long> counts = new EnumMap<>(OpOutcome.class);
    results.forEach(result -> counts.merge(result.outcome(), 1L, Long::sum));
    return counts;
  }
}
