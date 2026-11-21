package com.uit.finance.modules.recurring.adapter.in.messaging;

import com.uit.finance.shared.messaging.ConsumerQueues;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Queue của recurring (consumer khai báo thứ mình consume). */
@Configuration(proxyBeanMethods = false)
class RecurringQueues {

  static final String USER_DELETED_QUEUE = "recurring.user-deleted";
  static final String USER_DELETED_ROUTING_KEY = "identity.user.deleted";

  @Bean
  Declarables recurringUserDeletedQueueDeclarables() {
    return ConsumerQueues.forEvent(USER_DELETED_QUEUE, USER_DELETED_ROUTING_KEY);
  }
}
