package com.uit.finance.shared.kernel;

import java.time.Instant;
import java.util.UUID;

/**
 * Something that happened in the domain, expressed in past tense.
 *
 * <p>Events are integration contracts: they carry simple, serialisable values (UUID, String,
 * Instant, long) rather than rich value objects so that other modules and external consumers can
 * read them without depending on this module's domain model.
 */
public interface DomainEvent {

  /** Globally unique id used by consumers for de-duplication. */
  UUID eventId();

  Instant occurredAt();

  /** Routing key and type discriminator, e.g. {@code identity.user.registered}. */
  String type();

  /** Bump when the payload changes incompatibly; consumers run both versions side by side. */
  default int schemaVersion() {
    return 1;
  }
}
