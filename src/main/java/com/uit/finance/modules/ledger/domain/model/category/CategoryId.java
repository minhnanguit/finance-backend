package com.uit.finance.modules.ledger.domain.model.category;

import com.uit.finance.modules.ledger.domain.model.shared.Fields;
import com.uit.finance.shared.kernel.Ensure;
import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Id danh mục, do client sinh; riêng danh mục mặc định do server sinh bằng UUIDv5. */
public record CategoryId(UUID value) {

  /** ADR-005 §6. **Không bao giờ đổi**: đổi là mọi user bị seed trùng danh mục mặc định. */
  static final UUID TEMPLATE_NAMESPACE = UUID.fromString("369f5fab-8070-4d9d-86ba-0eeda1359ec9");

  public CategoryId {
    Ensure.notNull(value, "categoryId");
  }

  /** Id đến từ client: thiếu là {@code ledger.invalid_field}. */
  public static CategoryId of(@Nullable UUID raw) {
    return new CategoryId(Fields.required(raw, "categoryId"));
  }

  public static @Nullable CategoryId ofNullable(@Nullable UUID raw) {
    return raw == null ? null : new CategoryId(raw);
  }

  /** Cùng user + cùng mẫu luôn ra cùng id, nên seed lại bao nhiêu lần cũng không trùng. */
  public static CategoryId forTemplate(UserId owner, TemplateKey key) {
    return new CategoryId(NameBasedUuid.v5(TEMPLATE_NAMESPACE, owner.value() + ":" + key.value()));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
