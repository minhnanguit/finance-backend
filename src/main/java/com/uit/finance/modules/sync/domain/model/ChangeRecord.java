package com.uit.finance.modules.sync.domain.model;

import com.uit.finance.shared.kernel.Ensure;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Một bản ghi như client thấy khi pull, kể cả tombstone ({@code data = null}). */
public record ChangeRecord(
    String entity, UUID id, long changeSeq, boolean deleted, @Nullable Map<String, Object> data) {

  public ChangeRecord {
    Ensure.notBlank(entity, "entity");
    Ensure.notNull(id, "id");
    data = deleted || data == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(data));
  }
}
