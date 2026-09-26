package com.uit.finance.shared.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

/**
 * Standard consumer topology: a quorum queue bound to {@code finance.events} plus a parking-lot
 * queue that receives messages once retries are exhausted (see {@code spring.rabbitmq.listener}).
 *
 * <pre>
 *   finance.events --(routingKey)--> {queue}  --x failed after retries--> finance.dlx --> {queue}.parked
 * </pre>
 */
public final class ConsumerQueues {

  public static final String PARKED_SUFFIX = ".parked";

  private ConsumerQueues() {}

  public static Declarables forEvent(String queueName, String routingKey) {
    String parkedName = queueName + PARKED_SUFFIX;
    Queue main =
        QueueBuilder.durable(queueName)
            .quorum()
            .deadLetterExchange(FinanceExchanges.DLX)
            .deadLetterRoutingKey(parkedName)
            .build();
    Queue parked = QueueBuilder.durable(parkedName).quorum().build();
    Binding mainBinding =
        BindingBuilder.bind(main).to(new TopicExchange(FinanceExchanges.EVENTS)).with(routingKey);
    Binding parkedBinding =
        BindingBuilder.bind(parked).to(new TopicExchange(FinanceExchanges.DLX)).with(parkedName);
    return new Declarables(main, parked, mainBinding, parkedBinding);
  }
}
