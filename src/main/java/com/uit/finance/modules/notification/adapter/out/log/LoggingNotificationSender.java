package com.uit.finance.modules.notification.adapter.out.log;

import com.uit.finance.modules.notification.application.port.out.NotificationSenderPort;
import com.uit.finance.modules.notification.domain.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Core-phase channel: logs the message. Replace with push/e-mail adapters behind the same port. */
@Component
class LoggingNotificationSender implements NotificationSenderPort {

  private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

  @Override
  public void send(Notification notification) {
    log.info(
        "notification kind={} to user={} subject=\"{}\"",
        notification.kind(),
        notification.recipient().userId(),
        notification.subject());
  }
}
