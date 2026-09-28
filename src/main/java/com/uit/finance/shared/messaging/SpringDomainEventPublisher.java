package com.uit.finance.shared.messaging;

import com.uit.finance.shared.kernel.DomainEvent;
import com.uit.finance.shared.kernel.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Bridges the kernel port to Spring's event bus. Because the application service is transactional,
 * Spring Modulith persists the publication in the same transaction (transactional outbox).
 */
@Component
class SpringDomainEventPublisher implements DomainEventPublisher {

  private final ApplicationEventPublisher publisher;

  SpringDomainEventPublisher(ApplicationEventPublisher publisher) {
    this.publisher = publisher;
  }

  @Override
  public void publish(DomainEvent event) {
    publisher.publishEvent(event);
  }
}
