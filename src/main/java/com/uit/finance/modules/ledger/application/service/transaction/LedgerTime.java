package com.uit.finance.modules.ledger.application.service.transaction;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

final class LedgerTime {

  private LedgerTime() {}

  /** "Hôm nay" cho giới hạn {@code occurredOn} tính theo UTC (ADR-005 §4). */
  static LocalDate today(Clock clock) {
    return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
  }
}
