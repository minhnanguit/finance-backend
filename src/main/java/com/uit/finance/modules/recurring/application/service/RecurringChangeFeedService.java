package com.uit.finance.modules.recurring.application.service;

import com.uit.finance.modules.recurring.application.port.in.RecurringChangeFeedUseCase;
import com.uit.finance.modules.recurring.application.port.in.RecurringView;
import com.uit.finance.modules.recurring.application.port.out.LoadRecurringChangesPort;
import com.uit.finance.modules.recurring.application.port.out.Sequenced;
import com.uit.finance.modules.recurring.domain.model.Recurring;
import com.uit.finance.modules.recurring.domain.model.RecurringId;
import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class RecurringChangeFeedService implements RecurringChangeFeedUseCase {

  private final LoadRecurringChangesPort changes;

  RecurringChangeFeedService(LoadRecurringChangesPort changes) {
    this.changes = changes;
  }

  @Override
  public List<RecurringChange> since(UserId owner, long afterSeq, int limit) {
    return changes.changesSince(owner, afterSeq, limit).stream()
        .map(RecurringChangeFeedService::toChange)
        .toList();
  }

  @Override
  public Optional<RecurringChange> find(UserId owner, UUID recurringId) {
    return changes
        .findSequenced(owner, RecurringId.of(recurringId))
        .map(RecurringChangeFeedService::toChange);
  }

  private static RecurringChange toChange(Sequenced<Recurring> sequenced) {
    return new RecurringChange(RecurringView.from(sequenced.value()), sequenced.changeSeq());
  }
}
