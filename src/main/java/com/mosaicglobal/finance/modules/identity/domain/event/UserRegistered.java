package com.mosaicglobal.finance.modules.identity.domain.event;

import com.mosaicglobal.finance.modules.identity.domain.model.User;
import com.mosaicglobal.finance.shared.kernel.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Có account mới được tạo. Routing key {@value #TYPE}. */
public record UserRegistered(
    UUID eventId, Instant occurredAt, UUID userId, String email, String displayName)
    implements DomainEvent {

  public static final String TYPE = "identity.user.registered";

  public static UserRegistered from(User user, Instant now) {
    return new UserRegistered(
        UUID.randomUUID(),
        now,
        user.getId().value(),
        user.getEmail().value(),
        user.getDisplayName().value());
  }

  @Override
  public String type() {
    return TYPE;
  }
}
