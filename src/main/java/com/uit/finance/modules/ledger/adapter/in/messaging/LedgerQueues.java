package com.uit.finance.modules.ledger.adapter.in.messaging;

import com.uit.finance.shared.messaging.ConsumerQueues;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Queue mà ledger consume (consumer sở hữu queue của mình). */
@Configuration(proxyBeanMethods = false)
class LedgerQueues {

  static final String USER_DELETED_QUEUE = "ledger.user-deleted";
  static final String USER_DELETED_ROUTING_KEY = "identity.user.deleted";

  @Bean
  Declarables ledgerQueueDeclarables() {
    return ConsumerQueues.forEvent(USER_DELETED_QUEUE, USER_DELETED_ROUTING_KEY);
  }
}
