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
 * Transaction thứ hai của một op không được áp (ADR-002 §7 bước 2): ghi nhật ký, rồi đọc bản ghi
 * hiện tại sau khi lần ghi dở đã bị rollback.
 */
@Service
@Transactional
class RecordOpOutcomeService {

  private final OpLogPort opLog;
  private final SyncHandlersPort handlers;
  private final Clock clock;

  RecordOpOutcomeService(OpLogPort opLog, SyncHandlersPort handlers, Clock clock) {
    this.opLog = opLog;
    this.handlers = handlers;
    this.clock = clock;
  }

  OpResult record(UserId owner, DeviceId device, PushedOp op, OpVerdict verdict) {
    OpVerdict outcome = verdict;
    if (verdict.outcome() != OpOutcome.DUPLICATE
        && !opLog.record(owner, OpLogEntry.of(op, device, verdict, clock.instant()))) {
      outcome = OpVerdict.duplicate();
    }
    return new OpResult(
        op.opId(),
        outcome.outcome(),
        outcome.code(),
        handlers.current(owner, op.entity(), op.entityId()).orElse(null));
  }
}
