package com.uit.finance.modules.recurring.domain.model;

import com.uit.finance.shared.kernel.Ensure;
import java.util.UUID;

/** Id do client sinh (UUIDv4, ADR-008 §4). */
public record RecurringId(UUID value) {

  public RecurringId {
    Ensure.notNull(value, "value");
  }

  public static RecurringId of(UUID value) {
    return new RecurringId(value);
  }
}
