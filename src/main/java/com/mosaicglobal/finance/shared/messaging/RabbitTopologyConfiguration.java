package com.mosaicglobal.finance.shared.messaging;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declares the shared exchanges. Queues are declared by the module that consumes them. */
@Configuration(proxyBeanMethods = false)
class RabbitTopologyConfiguration {

  @Bean
  Declarables financeExchanges() {
    return new Declarables(
        ExchangeBuilder.topicExchange(FinanceExchanges.EVENTS).durable(true).build(),
        ExchangeBuilder.directExchange(FinanceExchanges.COMMANDS).durable(true).build(),
        ExchangeBuilder.topicExchange(FinanceExchanges.DLX).durable(true).build());
  }
}
