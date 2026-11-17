package com.uit.finance.modules.notification.domain.model;

import com.uit.finance.shared.kernel.Ensure;
import java.util.UUID;

public record NotificationId(UUID value) {

  public NotificationId {
    Ensure.notNull(value, "notificationId");
  }

  public static NotificationId newId() {
    return new NotificationId(UUID.randomUUID());
  }
}
