package com.mosaicglobal.finance.modules.notification.adapter.in.messaging;

import com.mosaicglobal.finance.shared.messaging.ConsumerQueues;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Queues owned by this module (a consumer declares what it consumes). */
@Configuration(proxyBeanMethods = false)
class NotificationQueues {

  static final String USER_REGISTERED_QUEUE = "notification.user-registered";
  static final String USER_REGISTERED_ROUTING_KEY = "identity.user.registered";

  @Bean
  Declarables notificationQueueDeclarables() {
    return ConsumerQueues.forEvent(USER_REGISTERED_QUEUE, USER_REGISTERED_ROUTING_KEY);
  }
}
