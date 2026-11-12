package com.uit.finance.shared.messaging;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcProcessedEventStore implements ProcessedEventStore {

  private static final String INSERT =
      """
      INSERT INTO processed_events (event_id, consumer, processed_at)
      VALUES (:eventId, :consumer, :processedAt)
      ON CONFLICT DO NOTHING
      """;

  private final JdbcClient jdbc;

  JdbcProcessedEventStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** {@code ctid} + {@code LIMIT}: Postgres has no {@code DELETE ... LIMIT}. */
  @Override
  public int deleteProcessedBefore(Instant cutoff, int batchSize) {
    return jdbc.sql(
            """
            DELETE FROM processed_events
            WHERE ctid IN (SELECT ctid FROM processed_events WHERE processed_at < :cutoff
                           LIMIT :batchSize)
            """)
        .param("cutoff", java.sql.Timestamp.from(cutoff))
        .param("batchSize", batchSize)
        .update();
  }

  @Override
  public boolean markProcessed(UUID eventId, String consumer, Instant at) {
    return jdbc.sql(INSERT)
            .param("eventId", eventId)
            .param("consumer", consumer)
            .param("processedAt", java.sql.Timestamp.from(at))
            .update()
        == 1;
  }
}
