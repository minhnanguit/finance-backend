package com.uit.finance.modules.sync.adapter.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SyncPropertiesTest {

  private static SyncProperties withRetention(Duration tombstones) {
    return new SyncProperties(Duration.ofDays(30), "0 30 3 * * *", tombstones, "0 45 3 * * *");
  }

  @Test
  @DisplayName("giữ tombstone ngắn hơn cửa sổ CREATE 90 ngày + 2 ngày → không khởi động (ADR-009)")
  void tombstonesMustOutliveTheCreateWindow() {
    assertThatThrownBy(() -> withRetention(Duration.ofDays(90)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatCode(() -> withRetention(SyncProperties.MIN_TOMBSTONE_RETENTION))
        .doesNotThrowAnyException();
  }
}
