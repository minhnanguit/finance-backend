package com.mosaicglobal.finance.shared.messaging;

import com.mosaicglobal.finance.shared.kernel.DomainEvent;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Wire format of every externalized event. Consumers rely on the header fields for de-duplication,
 * routing and schema evolution; {@code payload} carries the event itself.
 */
public record EventEnvelope<T>(
    UUID eventId,
    String type,
    int schemaVersion,
    Instant occurredAt,
    @Nullable String traceId,
    T payload) {

  public static EventEnvelope<DomainEvent> wrap(DomainEvent event, @Nullable String traceId) {
    return new EventEnvelope<>(
        event.eventId(), event.type(), event.schemaVersion(), event.occurredAt(), traceId, event);
  }
}
