package com.uit.finance.modules.identity.domain.event;

import com.uit.finance.shared.kernel.DomainEvent;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * User yêu cầu xoá tài khoản (ADR-008 A4): tài khoản đã bị khoá, thông tin cá nhân đã xoá. Trong
 * app chỉ dùng để mọi cache trạng thái của instance hiện tại biết ngay; vẫn được phát ra broker cho
 * consumer sau này (vd gửi email xác nhận). Dữ liệu của các module chỉ bị xoá khi có {@link
 * UserDeleted}. Routing key {@value #TYPE}. Chỉ mang id (ADR-006 B8).
 */
public record UserDeletionRequested(UUID eventId, Instant occurredAt, UUID userId)
    implements DomainEvent {

  public static final String TYPE = "identity.user.deletion-requested";

  public static UserDeletionRequested of(UserId userId, Instant now) {
    return new UserDeletionRequested(UUID.randomUUID(), now, userId.value());
  }

  @Override
  public String type() {
    return TYPE;
  }
}
