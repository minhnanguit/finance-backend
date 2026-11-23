package com.uit.finance.modules.recurring.application.port.in;

import com.uit.finance.modules.recurring.domain.model.Recurring;
import com.uit.finance.modules.recurring.domain.model.RecurringTerms;
import java.util.UUID;

/** Bản ghi khoản định kỳ hiện tại trên server, kèm {@code revision} hiện tại. */
public record RecurringView(UUID id, RecurringTerms terms, long revision, boolean deleted) {

  public static RecurringView from(Recurring recurring) {
    return new RecurringView(
        recurring.getId().value(),
        recurring.getTerms(),
        recurring.getRevision(),
        recurring.isDeleted());
  }
}
