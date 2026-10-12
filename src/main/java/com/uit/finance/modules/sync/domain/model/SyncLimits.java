package com.uit.finance.modules.sync.domain.model;

import java.time.Duration;

/** Giới hạn của sync (ADR-002 §4, ADR-006 B4). */
public final class SyncLimits {

  public static final int MAX_OPS_PER_PUSH = 100;
  public static final int DEFAULT_PULL_LIMIT = 200;
  public static final int MAX_PULL_LIMIT = 500;
  public static final Duration OP_LOG_RETENTION = Duration.ofDays(30);

  private SyncLimits() {}
}
