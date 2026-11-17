package com.uit.finance.modules.sync.adapter.in.messaging;

import com.uit.finance.shared.messaging.ConsumerQueues;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Queue mà sync consume (consumer sở hữu queue của mình). */
@Configuration(proxyBeanMethods = false)
class SyncQueues {

  static final String USER_DELETED_QUEUE = "sync.user-deleted";
  static final String USER_DELETED_ROUTING_KEY = "identity.user.deleted";

  @Bean
  Declarables syncQueueDeclarables() {
    return ConsumerQueues.forEvent(USER_DELETED_QUEUE, USER_DELETED_ROUTING_KEY);
  }
}
