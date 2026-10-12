package com.uit.finance.modules.sync.domain.model;

import com.uit.finance.shared.kernel.Ensure;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Một dòng nhật ký op: máy nào, lúc nào, entity nào, kết quả gì. Không có data (ADR-006 B8, B9).
 */
public record OpLogEntry(
    UUID opId,
    DeviceId deviceId,
    String entity,
    UUID entityId,
    OpAction action,
    OpOutcome outcome,
    @Nullable String code,
    Instant processedAt) {

  public OpLogEntry {
    Ensure.notNull(opId, "opId");
    Ensure.notNull(deviceId, "deviceId");
    Ensure.notBlank(entity, "entity");
    Ensure.notNull(entityId, "entityId");
    Ensure.notNull(action, "action");
    Ensure.notNull(outcome, "outcome");
    Ensure.notNull(processedAt, "processedAt");
    if (outcome == OpOutcome.DUPLICATE) {
      throw new IllegalArgumentException("DUPLICATE is never logged; the first outcome stays");
    }
  }

  public static OpLogEntry of(
      PushedOp op, DeviceId deviceId, OpVerdict verdict, Instant processedAt) {
    return new OpLogEntry(
        op.opId(),
        deviceId,
        op.entity(),
        op.entityId(),
        op.action(),
        verdict.outcome(),
        verdict.code(),
        processedAt);
  }
}
