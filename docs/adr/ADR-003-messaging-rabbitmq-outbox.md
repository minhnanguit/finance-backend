# ADR-003 – RabbitMQ as the central queue, reached only through the transactional outbox

**Status:** accepted · 2026-09-12

## Context
Async work (welcome messages, report aggregation, scheduled jobs) must not run inside the HTTP request, must survive restarts, and must not be lost when the database transaction rolls back. Kafka was considered.

## Decision
- **RabbitMQ** (quorum queues) is the message broker for all async paths. Kafka is not introduced until replay or stream analytics is an actual requirement.
- Domain code publishes `DomainEvent`s through the kernel port `DomainEventPublisher`. **Spring Modulith's event publication registry** (`event_publication`, Flyway-managed) is the outbox: the event row commits with the business data.
- `shared.messaging.EventExternalizationConfig` externalizes every `DomainEvent` after commit to exchange `finance.events` with routing key `event.type()`, wrapped in an `EventEnvelope` (`eventId, type, schemaVersion, occurredAt, traceId, payload`). Completed publications are archived.
- Topology: `finance.events` (topic), `finance.commands` (direct), `finance.dlx` (topic). Each consumer owns a quorum queue built by `ConsumerQueues.forEvent(queue, routingKey)` with dead-lettering to `<queue>.parked`.
- Retry: Spring AMQP stateless retry with exponential back-off (1s ×2, max 5 attempts, cap 30s) in the consumer; exhausted → parking lot for human inspection. No delayed-message plugin needed.
- Acknowledgement: container-managed (`acknowledge-mode: auto` = ack after the listener returns, reject on exception). Publisher confirms are on; the externalizer completes the outbox entry only after the broker confirms.
- Consumers are idempotent: `EventDeduplicator.executeOnce(eventId, consumer, handler)` inserts into `processed_events` and runs the handler in the same transaction.

## Consequences
- At-least-once delivery end to end, exactly-once *effect* per consumer.
- Producers know nothing about the broker; swapping RabbitMQ means changing `shared.messaging` and a dependency.
- Message payloads are the events themselves, so events must stay flat and versioned (`schemaVersion`).
