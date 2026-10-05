package com.uit.finance.modules.sync.application.service;

import com.uit.finance.modules.sync.application.port.out.OpLogPort;
import com.uit.finance.modules.sync.application.port.out.SyncHandlersPort;
import com.uit.finance.modules.sync.domain.model.DeviceId;
import com.uit.finance.modules.sync.domain.model.OpLogEntry;
import com.uit.finance.modules.sync.domain.model.OpOutcome;
import com.uit.finance.modules.sync.domain.model.OpResult;
import com.uit.finance.modules.sync.domain.model.OpVerdict;
import com.uit.finance.modules.sync.domain.model.PushedOp;
import com.uit.finance.shared.kernel.UserId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction của một op (ADR-002 §7 bước 1). Use case của module dữ liệu chạy {@code REQUIRED} nên
 * join vào đây; nhật ký {@code APPLIED} commit cùng dữ liệu.
 *
 * <p>Op không được áp thì ném {@link OpNotAppliedException} để rollback cả lần ghi dở lẫn số {@code
 * change_seq}; kết quả được ghi ở {@link RecordOpOutcomeService}, transaction thứ hai.
 */
@Service
@Transactional
class ApplyOpService {

  private final OpLogPort opLog;
  private final SyncHandlersPort handlers;
  private final Clock clock;

  ApplyOpService(OpLogPort opLog, SyncHandlersPort handlers, Clock clock) {
    this.opLog = opLog;
    this.handlers = handlers;
    this.clock = clock;
  }

  OpResult apply(UserId owner, DeviceId device, PushedOp op) {
    boolean alreadyFinal =
        opLog.findOutcome(owner, op.opId()).map(OpOutcome::isFinal).orElse(false);
    if (alreadyFinal) {
      return result(owner, op, OpVerdict.duplicate());
    }
    if (!handlers.supports(op.entity())) {
      throw new OpNotAppliedException(OpVerdict.unknownEntity());
    }

    OpVerdict verdict = handlers.apply(owner, op);
    if (verdict.outcome() != OpOutcome.APPLIED) {
      throw new OpNotAppliedException(verdict);
    }
    // Request song song mang cùng opId đã chốt trước: huỷ lần áp này.
    if (!opLog.record(owner, OpLogEntry.of(op, device, verdict, clock.instant()))) {
      throw new OpNotAppliedException(OpVerdict.duplicate());
    }
    return result(owner, op, verdict);
  }

  private OpResult result(UserId owner, PushedOp op, OpVerdict verdict) {
    return new OpResult(
        op.opId(),
        verdict.outcome(),
        verdict.code(),
        handlers.current(owner, op.entity(), op.entityId()).orElse(null));
  }
}
