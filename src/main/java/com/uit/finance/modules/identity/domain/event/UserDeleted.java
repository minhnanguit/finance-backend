package com.uit.finance.modules.identity.domain.event;

import com.uit.finance.shared.kernel.DomainEvent;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * Tài khoản đã hết thời gian chờ xoá: mọi module có dữ liệu của user phải xoá thật (ADR-008 A7).
 * Phát sau khi không còn token nào của user dùng được, nên không có lần ghi nào tới sau khi module
 * đã dọn. Routing key {@value #TYPE}. Chỉ mang id (ADR-006 B8).
 */
public record UserDeleted(UUID eventId, Instant occurredAt, UUID userId) implements DomainEvent {

  public static final String TYPE = "identity.user.deleted";

  public static UserDeleted of(UserId userId, Instant now) {
    return new UserDeleted(UUID.randomUUID(), now, userId.value());
  }

  @Override
  public String type() {
    return TYPE;
  }
}
