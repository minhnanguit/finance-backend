package com.mosaicglobal.finance.modules.notification.application.port.in;

import java.util.UUID;

public interface SendWelcomeNotificationUseCase {

  void send(SendWelcomeNotificationCommand command);

  record SendWelcomeNotificationCommand(UUID userId, String email, String displayName) {}
}
