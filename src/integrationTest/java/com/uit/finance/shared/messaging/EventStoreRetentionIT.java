package com.uit.finance.shared.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.TestIdentityProvider;
import com.uit.finance.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Archive của outbox và dấu chống trùng quá hạn bị xoá (ADR-008): archive giữ nguyên payload, kể cả
 * email trong {@code identity.user.registered}, nên không được sống lâu hơn dữ liệu nó mô tả.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EventStoreRetentionIT {

  @Autowired EventStoreRetentionJob job;
  @Autowired JdbcClient jdbc;

  @DynamicPropertySource
  static void identityProvider(DynamicPropertyRegistry registry) {
    TestIdentityProvider idp = TestIdentityProvider.instance();
    registry.add("app.security.issuer-uri", () -> TestIdentityProvider.ISSUER);
    registry.add("app.security.jwk-set-uri", idp::jwkSetUri);
    registry.add("app.security.audience", () -> TestIdentityProvider.AUDIENCE);
  }

  private void archived(UUID id, String age) {
    jdbc.sql(
            """
            INSERT INTO event_publication_archive
              (id, listener_id, event_type, serialized_event, publication_date, completion_date,
               status, completion_attempts)
            VALUES (:id, 'it.listener', 'it.Event', '{"email":"ann@example.com"}',
                    now() - CAST(:age AS interval), now() - CAST(:age AS interval), 'COMPLETED', 1)
            """)
        .param("id", id)
        .param("age", age)
        .update();
  }

  private void processed(UUID eventId, String age) {
    jdbc.sql(
            """
            INSERT INTO processed_events (event_id, consumer, processed_at)
            VALUES (:id, 'it.consumer', now() - CAST(:age AS interval))
            """)
        .param("id", eventId)
        .param("age", age)
        .update();
  }

  private long count(String table, String column, UUID id) {
    return jdbc.sql("SELECT count(*) FROM " + table + " WHERE " + column + " = :id")
        .param("id", id)
        .query(Long.class)
        .single();
  }

  @Test
  @DisplayName("archive và dấu chống trùng cũ hơn 30 ngày bị xoá, cái mới còn nguyên")
  void expiredBookkeepingIsPurged() {
    UUID oldEvent = UUID.randomUUID();
    UUID recentEvent = UUID.randomUUID();
    archived(oldEvent, "40 days");
    archived(recentEvent, "1 day");
    processed(oldEvent, "40 days");
    processed(recentEvent, "1 day");

    job.purgeExpired();

    assertThat(count("event_publication_archive", "id", oldEvent)).isZero();
    assertThat(count("event_publication_archive", "id", recentEvent)).isEqualTo(1);
    assertThat(count("processed_events", "event_id", oldEvent)).isZero();
    assertThat(count("processed_events", "event_id", recentEvent)).isEqualTo(1);
  }
}
