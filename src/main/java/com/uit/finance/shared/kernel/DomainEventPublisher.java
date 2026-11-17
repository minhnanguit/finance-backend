package com.uit.finance.shared.kernel;

import java.util.Collection;

/**
 * Outbound port through which the application layer publishes domain events.
 *
 * <p>The infrastructure implementation (shared.messaging) records the event inside the current
 * transaction (transactional outbox) and delivers it to in-process listeners and, when configured,
 * to the message broker after commit.
 */
public interface DomainEventPublisher {

  void publish(DomainEvent event);

  default void publishAll(Collection<? extends DomainEvent> events) {
    events.forEach(this::publish);
  }
}
