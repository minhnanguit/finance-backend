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
     * Không ngắn hơn {@link #MIN_TOMBSTONE_RETENTION}.
     */
    @DefaultValue("92d") Duration tombstoneRetention,
    /** Lịch chạy job dọn tombstone, cron 6 trường của Spring. */
    @DefaultValue("0 45 3 * * *") String tombstonePurgeCron) {

  /**
   * Op {@code CREATE} (ADR-009 §3) chỉ chống được việc sinh lại một id đã xoá khi tombstone còn.
   * Client chỉ sinh id tất định cho ngày trong {@value #CREATE_WINDOW_DAYS} ngày gần nhất (kỳ định
   * kỳ ghi bù tối đa 90 ngày, ADR-009 R7; nháp từ thông báo ngân hàng luôn là ngày hiện tại), nên
   * tombstone phải sống lâu hơn cửa sổ đó, cộng 2 ngày dung sai múi giờ quanh UTC.
   */
  public static final int CREATE_WINDOW_DAYS = 90;

  public static final Duration MIN_TOMBSTONE_RETENTION = Duration.ofDays(CREATE_WINDOW_DAYS + 2);

  public SyncProperties {
    if (tombstoneRetention.compareTo(MIN_TOMBSTONE_RETENTION) < 0) {
      throw new IllegalArgumentException(
          "app.sync.tombstone-retention must be >= "
              + MIN_TOMBSTONE_RETENTION.toDays()
              + "d (ADR-009: CREATE window + time zone margin)");
    }
    if (tombstoneRetention.compareTo(opLogRetention) < 0) {
      throw new IllegalArgumentException(
          "app.sync.tombstone-retention must be >= app.sync.op-log-retention");
    }
  }
}
