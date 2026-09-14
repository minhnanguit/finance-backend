package com.mosaicglobal.finance.shared.messaging;

import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Idempotent consumption: the de-duplication mark and the handler run in ONE transaction, so a
 * handler failure releases the mark and the broker redelivers; a duplicate delivery is skipped.
 *
 * <p>Uses a programmatic transaction on purpose – {@code @Transactional} is reserved for
 * application services (architecture rule 8); this is infrastructure plumbing.
 */
@Component
public class EventDeduplicator {

  private static final Logger log = LoggerFactory.getLogger(EventDeduplicator.class);

  private final ProcessedEventStore store;
  private final TransactionTemplate transaction;
  private final Clock clock;

  EventDeduplicator(ProcessedEventStore store, PlatformTransactionManager txManager, Clock clock) {
    this.store = store;
    this.transaction = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  public void executeOnce(UUID eventId, String consumer, Runnable handler) {
    transaction.executeWithoutResult(
        status -> {
          if (!store.markProcessed(eventId, consumer, clock.instant())) {
            log.debug("Skipping duplicate event {} for consumer {}", eventId, consumer);
            return;
          }
          handler.run();
        });
  }
}
