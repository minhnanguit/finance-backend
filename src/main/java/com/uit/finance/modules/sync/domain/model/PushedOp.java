package com.uit.finance.modules.sync.domain.model;

import com.uit.finance.modules.sync.domain.exception.InvalidBatchException;
import com.uit.finance.shared.kernel.Ensure;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Một op client gửi lên. {@code data} chưa kiểm tra; module sở hữu entity sẽ đọc chặt. */
public record PushedOp(
    UUID opId, String entity, UUID entityId, OpAction action, @Nullable Map<String, Object> data) {

  public static final int MAX_ENTITY_LENGTH = 30;

  public PushedOp {
    Ensure.notNull(opId, "opId");
    Ensure.notBlank(entity, "entity");
    Ensure.notNull(entityId, "entityId");
    Ensure.notNull(action, "action");
    if (entity.length() > MAX_ENTITY_LENGTH) {
      throw new InvalidBatchException("entity name is too long");
    }
    data = data == null ? null : Collections.unmodifiableMap(new HashMap<>(data));
  }

  /** Không in {@code data}: có thể chứa số tiền, ghi chú (ADR-006 B8). */
  @Override
  public String toString() {
    return "PushedOp[opId=%s, entity=%s, entityId=%s, action=%s]"
        .formatted(opId, entity, entityId, action);
  }
}
