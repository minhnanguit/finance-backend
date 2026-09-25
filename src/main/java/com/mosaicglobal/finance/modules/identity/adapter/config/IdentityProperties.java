package com.mosaicglobal.finance.modules.identity.adapter.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param subjectCacheTtl TTL của mapping {@code sub} → user id trong cache, không đụng DB. Cũng là
 *     độ stale tối đa khi user đổi email/tên trên Keycloak.
 * @param subjectCacheSize số {@code sub} tối đa giữ in-memory — tính theo concurrent active user,
 *     không phải tổng số user.
 */
@ConfigurationProperties(prefix = "app.identity")
public record IdentityProperties(
    @DefaultValue("10m") Duration subjectCacheTtl, @DefaultValue("10000") long subjectCacheSize) {}
