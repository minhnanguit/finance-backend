package com.uit.finance.shared.kernel;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for aggregate roots that record domain events while state changes, to be published by
 * the application service once the unit of work succeeds.
 */
public abstract class AggregateRoot {

  private final List<DomainEvent> pendingEvents = new ArrayList<>();

  protected void registerEvent(DomainEvent event) {
    pendingEvents.add(Ensure.notNull(event, "event"));
  }

  /** Returns and clears the recorded events. */
  public List<DomainEvent> pullDomainEvents() {
    List<DomainEvent> copy = List.copyOf(pendingEvents);
    pendingEvents.clear();
    return copy;
  }
}
