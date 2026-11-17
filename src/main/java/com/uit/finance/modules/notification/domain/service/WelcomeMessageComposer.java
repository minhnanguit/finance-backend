package com.uit.finance.modules.notification.domain.service;

import com.uit.finance.modules.notification.domain.model.Notification;
import com.uit.finance.modules.notification.domain.model.NotificationId;
import com.uit.finance.modules.notification.domain.model.NotificationKind;
import com.uit.finance.modules.notification.domain.model.Recipient;
import java.time.Instant;

/** Domain service: the wording rules for the welcome message. Pure, no framework. */
public final class WelcomeMessageComposer {

  private WelcomeMessageComposer() {}

  public static Notification compose(Recipient recipient, Instant now) {
    return new Notification(
        NotificationId.newId(),
        recipient,
        NotificationKind.WELCOME,
        "Welcome to Finance, " + recipient.displayName(),
        "Your account is ready. Start by adding your first wallet and recording an expense.",
        now);
  }
}
