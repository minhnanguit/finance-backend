package com.uit.finance.shared.sync;

import com.uit.finance.shared.kernel.Ensure;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Một bản ghi như client sẽ thấy khi pull, kể cả tombstone.
 *
 * @param data field nghiệp vụ theo contract ({@code AccountData}...); {@code null} khi đã xoá. Giữ
 *     thứ tự key để JSON dễ đọc; value {@code null} được giữ nguyên
 */
public record SyncChange(
    String entity, UUID id, long changeSeq, boolean deleted, @Nullable Map<String, Object> data) {

  public SyncChange {
    Ensure.notBlank(entity, "entity");
    Ensure.notNull(id, "id");
    if (changeSeq < 1) {
      throw new IllegalArgumentException("changeSeq starts at 1");
    }
    data = deleted || data == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(data));
  }
}
