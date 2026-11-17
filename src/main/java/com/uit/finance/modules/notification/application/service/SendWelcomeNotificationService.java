package com.uit.finance.modules.notification.application.service;

import com.uit.finance.modules.notification.application.port.in.SendWelcomeNotificationUseCase;
import com.uit.finance.modules.notification.application.port.out.NotificationSenderPort;
import com.uit.finance.modules.notification.domain.model.Recipient;
import com.uit.finance.modules.notification.domain.service.WelcomeMessageComposer;
import com.uit.finance.shared.kernel.UserId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class SendWelcomeNotificationService implements SendWelcomeNotificationUseCase {

  private final NotificationSenderPort sender;
  private final Clock clock;

  SendWelcomeNotificationService(NotificationSenderPort sender, Clock clock) {
    this.sender = sender;
    this.clock = clock;
  }

  @Override
  public void send(SendWelcomeNotificationCommand command) {
    Recipient recipient =
        new Recipient(new UserId(command.userId()), command.email(), command.displayName());
    sender.send(WelcomeMessageComposer.compose(recipient, clock.instant()));
  }
}
