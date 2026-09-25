package com.mosaicglobal.finance.modules.identity.adapter.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param subjectCacheTtl how long a resolved subject → user id mapping is trusted without touching
 *     the database. Also the upper bound on how stale a locally cached profile can be.
 * @param subjectCacheSize cap on distinct subjects held in memory; sized for concurrently active
 *     users, not total users.
 */
@ConfigurationProperties(prefix = "app.identity")
public record IdentityProperties(
    @DefaultValue("10m") Duration subjectCacheTtl, @DefaultValue("10000") long subjectCacheSize) {}
