package com.uit.finance.shared.messaging;

import java.time.Instant;
import java.util.UUID;

/** Remembers which (event, consumer) pairs were already handled. */
public interface ProcessedEventStore {

  /** Returns true if this call claimed the event; false if it was processed before. */
  boolean markProcessed(UUID eventId, String consumer, Instant at);
}
