package com.mosaicglobal.finance.modules.notification.domain.model;

import com.mosaicglobal.finance.shared.kernel.Ensure;
import java.time.Instant;

/** A message to deliver to a user. Immutable; delivery state is the sender's concern. */
public record Notification(
    NotificationId id,
    Recipient recipient,
    NotificationKind kind,
    String subject,
    String body,
    Instant createdAt) {

  public Notification {
    Ensure.notNull(id, "id");
    Ensure.notNull(recipient, "recipient");
    Ensure.notNull(kind, "kind");
    Ensure.lengthBetween(subject, 1, 200, "subject");
    Ensure.notBlank(body, "body");
    Ensure.notNull(createdAt, "createdAt");
  }
}
