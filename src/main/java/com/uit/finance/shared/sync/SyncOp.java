package com.uit.finance.shared.sync;

import com.uit.finance.shared.kernel.Ensure;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Một thay đổi client gửi lên.
 *
 * @param data JSON đã giải mã, chưa kiểm tra; đọc bằng {@link SyncData}. {@code null} khi {@code
 *     DELETE}
 */
public record SyncOp(
    UUID opId, String entity, UUID id, SyncAction action, @Nullable Map<String, Object> data) {

  public SyncOp {
    Ensure.notNull(opId, "opId");
    Ensure.notBlank(entity, "entity");
    Ensure.notNull(id, "id");
    Ensure.notNull(action, "action");
    // JSON null là hợp lệ nên không dùng Map.copyOf (không nhận value null).
    data = data == null ? null : Collections.unmodifiableMap(new HashMap<>(data));
  }
}
