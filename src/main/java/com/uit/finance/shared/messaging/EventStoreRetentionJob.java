package com.uit.finance.shared.messaging;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily cleanup of finished messaging bookkeeping (ADR-008): archived event publications and
 * consumer de-duplication marks older than {@link EventStoreRetentionProperties#retention()}.
 *
 * <p>Without it the archive keeps every payload forever, including personal data of users who have
 * deleted their account. Safe on many instances at once: every statement is an idempotent delete.
 */
@Component
@EnableScheduling
@EnableConfigurationProperties(EventStoreRetentionProperties.class)
class EventStoreRetentionJob {

  static final int BATCH_SIZE = 5_000;
  private static final Logger log = LoggerFactory.getLogger(EventStoreRetentionJob.class);

  private final CompletedEventPublications completedPublications;
  private final ProcessedEventStore processedEvents;
  private final EventStoreRetentionProperties properties;
  private final Clock clock;

  EventStoreRetentionJob(
      CompletedEventPublications completedPublications,
      ProcessedEventStore processedEvents,
      EventStoreRetentionProperties properties,
      Clock clock) {
    this.completedPublications = completedPublications;
    this.processedEvents = processedEvents;
    this.properties = properties;
    this.clock = clock;
  }

  @Scheduled(cron = "${app.messaging.cleanup-cron:0 15 4 * * *}")
  void purgeExpired() {
    completedPublications.deletePublicationsOlderThan(properties.retention());
    Instant cutoff = clock.instant().minus(properties.retention());
    int marks = 0;
    int deleted;
    do {
      deleted = processedEvents.deleteProcessedBefore(cutoff, BATCH_SIZE);
      marks += deleted;
    } while (deleted == BATCH_SIZE);
    log.info("messaging retention purge processedMarks={}", marks);
  }
}
