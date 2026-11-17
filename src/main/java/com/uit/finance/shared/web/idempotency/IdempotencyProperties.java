package com.uit.finance.shared.web.idempotency;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tuning for the {@link IdempotencyFilter}. */
@ConfigurationProperties(prefix = "app.idempotency")
public record IdempotencyProperties(
    /** How long a completed response is replayable. */
    @DefaultValue("24h") Duration ttl,
    /** Lock duration while the first request is still executing. */
    @DefaultValue("30s") Duration inProgressTtl,
    /** Only requests whose path starts with one of these are protected. */
    @DefaultValue("/api/") List<String> protectedPathPrefixes,
    /** Requests above this size are rejected with 413 instead of being fingerprinted. */
    @DefaultValue("1048576") int maxBodyBytes) {}
