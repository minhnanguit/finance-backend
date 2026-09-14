package com.mosaicglobal.finance.modules.notification.domain.model;

import com.mosaicglobal.finance.shared.kernel.Ensure;
import com.mosaicglobal.finance.shared.kernel.UserId;

public record Recipient(UserId userId, String email, String displayName) {

  public Recipient {
    Ensure.notNull(userId, "recipient.userId");
    Ensure.notBlank(email, "recipient.email");
    Ensure.notBlank(displayName, "recipient.displayName");
  }
}
