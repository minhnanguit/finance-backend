package com.uit.finance.modules.recurring.application.service;

import com.uit.finance.modules.recurring.application.port.in.DeleteRecurringUseCase;
import com.uit.finance.modules.recurring.application.port.in.RecurringView;
import com.uit.finance.modules.recurring.application.port.out.LoadRecurringPort;
import com.uit.finance.modules.recurring.application.port.out.RecurringWriteLockPort;
import com.uit.finance.modules.recurring.application.port.out.SaveRecurringPort;
import com.uit.finance.modules.recurring.domain.exception.RecurringNotFoundException;
import com.uit.finance.modules.recurring.domain.model.Recurring;
import com.uit.finance.modules.recurring.domain.model.RecurringId;
import com.uit.finance.shared.kernel.UserId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class DeleteRecurringService implements DeleteRecurringUseCase {

  private final RecurringWriteLockPort writeLock;
  private final LoadRecurringPort load;
  private final SaveRecurringPort save;
  private final Clock clock;

  DeleteRecurringService(
      RecurringWriteLockPort writeLock,
      LoadRecurringPort load,
      SaveRecurringPort save,
      Clock clock) {
    this.writeLock = writeLock;
    this.load = load;
    this.save = save;
    this.clock = clock;
  }

  @Override
  public RecurringView delete(DeleteRecurringCommand command) {
    UserId owner = command.userId();
    writeLock.lock(owner);
    RecurringId id = RecurringId.of(command.recurringId());
    Recurring recurring =
        load.find(owner, id).orElseThrow(() -> new RecurringNotFoundException(id.value()));
    if (recurring.delete(clock.instant())) {
      save.update(owner, recurring);
    }
    return RecurringView.from(recurring);
  }
}
