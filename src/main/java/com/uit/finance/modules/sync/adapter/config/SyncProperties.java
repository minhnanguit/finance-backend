package com.uit.finance.modules.sync.adapter.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.sync")
public record SyncProperties(
    /** Giữ nhật ký op bao lâu (ADR-002 §7). */
    @DefaultValue("30d") Duration opLogRetention,
    /** Lịch chạy job dọn nhật ký, cron 6 trường của Spring. */
    @DefaultValue("0 30 3 * * *") String opLogPurgeCron) {}
