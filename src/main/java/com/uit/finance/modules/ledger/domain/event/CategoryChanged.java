package com.uit.finance.modules.ledger.domain.event;

import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.shared.kernel.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Danh mục bị xoá hoặc đổi cha (ADR-007 §7). Không phát khi đổi tên, màu, icon hay archive.
 * Consumer phải đọc lại trạng thái hiện tại qua {@code port/in/reference}, không suy ra từ event.
 * Routing key {@value #TYPE}.
 */
public record CategoryChanged(UUID eventId, Instant occurredAt, UUID categoryId, UUID userId)
    implements DomainEvent {

  public static final String TYPE = "ledger.category.changed";

  public static CategoryChanged of(Category category, Instant now) {
    return new CategoryChanged(
        UUID.randomUUID(), now, category.getId().value(), category.getOwner().value());
  }

  @Override
  public String type() {
    return TYPE;
  }
}
