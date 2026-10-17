package com.uit.finance.modules.budget.adapter.in.messaging;

import com.uit.finance.shared.messaging.ConsumerQueues;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Queue của budget (consumer khai báo thứ mình consume). */
@Configuration(proxyBeanMethods = false)
class BudgetQueues {

  static final String CATEGORY_CHANGED_QUEUE = "budget.category-changed";
  static final String CATEGORY_CHANGED_ROUTING_KEY = "ledger.category.changed";

  @Bean
  Declarables budgetQueueDeclarables() {
    return ConsumerQueues.forEvent(CATEGORY_CHANGED_QUEUE, CATEGORY_CHANGED_ROUTING_KEY);
  }
}
