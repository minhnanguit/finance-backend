package com.uit.finance.shared.messaging;

import com.uit.finance.shared.kernel.DomainEvent;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

/**
 * Every {@link DomainEvent} published in a transaction is (1) recorded in the outbox table by
 * Spring Modulith, (2) delivered to in-process listeners and (3) externalized to RabbitMQ after
 * commit – wrapped in an {@link EventEnvelope} and routed by {@link DomainEvent#type()}.
 *
 * <p>Domain code carries no broker annotations; this bean is the single switch for that behaviour.
 */
@Configuration(proxyBeanMethods = false)
class EventExternalizationConfig {

  static final String HEADER_EVENT_ID = "x-event-id";
  static final String HEADER_EVENT_TYPE = "x-event-type";
  static final String HEADER_SCHEMA_VERSION = "x-schema-version";

  @Bean
  EventExternalizationConfiguration eventExternalizationConfiguration(
      ObjectProvider<Tracer> tracer) {
    return EventExternalizationConfiguration.externalizing()
        .select(event -> event instanceof DomainEvent)
        .routeAll(
            event ->
                RoutingTarget.forTarget(FinanceExchanges.EVENTS)
                    .andKey(((DomainEvent) event).type()))
        .mapping(DomainEvent.class, event -> EventEnvelope.wrap(event, currentTraceId(tracer)))
        .headers(
            DomainEvent.class,
            event ->
                Map.of(
                    HEADER_EVENT_ID, event.eventId().toString(),
                    HEADER_EVENT_TYPE, event.type(),
                    HEADER_SCHEMA_VERSION, event.schemaVersion()))
        .build();
  }

  private static @Nullable String currentTraceId(ObjectProvider<Tracer> tracer) {
    Tracer current = tracer.getIfAvailable();
    if (current == null) {
      return null;
    }
    Span span = current.currentSpan();
    return span == null ? null : span.context().traceId();
  }
}
