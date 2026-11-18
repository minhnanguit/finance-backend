package com.uit.finance.modules.sync.adapter.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.sync")
public record SyncProperties(
    /** Giữ nhật ký op bao lâu (ADR-002 §7). */
    @DefaultValue("30d") Duration opLogRetention,
    /** Lịch chạy job dọn nhật ký, cron 6 trường của Spring. */
    @DefaultValue("0 30 3 * * *") String opLogPurgeCron,
    /**
     * Giữ tombstone bao lâu trước khi xoá thật (ADR-008 T1, T5). Dài hơn phiên đăng nhập offline
     * (30 ngày) và nhật ký op: máy offline ít hơn chừng này không bao giờ phải pull lại từ đầu.
     */
    @DefaultValue("90d") Duration tombstoneRetention,
    /** Lịch chạy job dọn tombstone, cron 6 trường của Spring. */
    @DefaultValue("0 45 3 * * *") String tombstonePurgeCron) {

  public SyncProperties {
    if (tombstoneRetention.compareTo(opLogRetention) < 0) {
      throw new IllegalArgumentException(
          "app.sync.tombstone-retention must be >= app.sync.op-log-retention");
    }
  }
}
