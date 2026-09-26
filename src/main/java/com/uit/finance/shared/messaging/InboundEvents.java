package com.uit.finance.shared.messaging;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Decodes an incoming message body into a typed {@link EventEnvelope}. */
@Component
public class InboundEvents {

  private final ObjectMapper objectMapper;

  public InboundEvents(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public <T> EventEnvelope<T> decode(String json, Class<T> payloadType) {
    RawEnvelope raw = objectMapper.readValue(json, RawEnvelope.class);
    T payload = objectMapper.treeToValue(raw.payload(), payloadType);
    return new EventEnvelope<>(
        raw.eventId(), raw.type(), raw.schemaVersion(), raw.occurredAt(), raw.traceId(), payload);
  }

  private record RawEnvelope(
      UUID eventId,
      String type,
      int schemaVersion,
      Instant occurredAt,
      @Nullable String traceId,
      JsonNode payload) {}
}
