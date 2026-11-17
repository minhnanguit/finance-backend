package com.uit.finance.modules.notification.application.port.out;

import com.uit.finance.modules.notification.domain.model.Notification;

/** Delivery channel (push, e-mail, log...). */
public interface NotificationSenderPort {

  void send(Notification notification);
}
