package com.uit.finance.modules.sync.domain.model;

import com.uit.finance.shared.kernel.Ensure;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Kết quả trả cho client của một op.
 *
 * @param current bản ghi hiện tại của chính user trên server, đọc sau khi transaction của op kết
 *     thúc; {@code null} khi không có hoặc không phải của user (ADR-006 B2)
 */
public record OpResult(
    UUID opId, OpOutcome outcome, @Nullable String code, @Nullable ChangeRecord current) {

  public OpResult {
    Ensure.notNull(opId, "opId");
    Ensure.notNull(outcome, "outcome");
  }
}
