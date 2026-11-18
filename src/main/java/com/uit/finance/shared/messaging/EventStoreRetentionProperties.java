package com.uit.finance.shared.messaging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How long the platform keeps finished messaging bookkeeping (ADR-008).
 *
 * @param retention completed event publications ({@code event_publication_archive}) and consumer
 *     de-duplication marks ({@code processed_events}) older than this are deleted. Archived events
 *     can carry personal data (e.g. {@code identity.user.registered} has the e-mail), so they must
 *     not outlive the data they describe.
 * @param cleanupCron when the cleanup runs, 6-field Spring cron
 */
@ConfigurationProperties(prefix = "app.messaging")
public record EventStoreRetentionProperties(
    @DefaultValue("30d") Duration retention, @DefaultValue("0 15 4 * * *") String cleanupCron) {}
