package com.uit.finance.modules.recurring.application.service;

import com.uit.finance.modules.ledger.application.port.in.reference.LedgerReferenceQuery;
import com.uit.finance.modules.recurring.application.port.in.RecurringView;
import com.uit.finance.modules.recurring.application.port.in.UpdateRecurringUseCase;
import com.uit.finance.modules.recurring.application.port.out.LoadRecurringPort;
import com.uit.finance.modules.recurring.application.port.out.RecurringWriteLockPort;
import com.uit.finance.modules.recurring.application.port.out.SaveRecurringPort;
import com.uit.finance.modules.recurring.domain.exception.RecurringNotFoundException;
import com.uit.finance.modules.recurring.domain.model.RecurrenceRevision;
import com.uit.finance.modules.recurring.domain.model.Recurring;
import com.uit.finance.modules.recurring.domain.model.RecurringId;
import com.uit.finance.modules.recurring.domain.model.RecurringTerms;
import com.uit.finance.shared.kernel.UserId;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class UpdateRecurringService implements UpdateRecurringUseCase {

  private final RecurringWriteLockPort writeLock;
  private final LoadRecurringPort load;
  private final SaveRecurringPort save;
  private final RecurringReferences references;
  private final Clock clock;

  UpdateRecurringService(
      RecurringWriteLockPort writeLock,
      LoadRecurringPort load,
      SaveRecurringPort save,
      LedgerReferenceQuery ledger,
      Clock clock) {
    this.writeLock = writeLock;
    this.load = load;
    this.save = save;
    this.references = new RecurringReferences(ledger);
    this.clock = clock;
  }

  @Override
  public RecurringView update(UpdateRecurringCommand command) {
    UserId owner = command.userId();
    writeLock.lock(owner);
    RecurringId id = RecurringId.of(command.recurringId());
    Recurring recurring =
        load.find(owner, id).orElseThrow(() -> new RecurringNotFoundException(id.value()));
    // Xoá luôn thắng (S4): bản đã xoá trả CONFLICT trước khi xét tới dữ liệu gửi lên.
    recurring.requireNotDeleted();
    LocalDate today = RecurringParser.today(clock);
    RecurringTerms next = RecurringParser.terms(command.fields());

    List<RecurrenceRevision> changed = recurring.changedRevisions(next);
    if (recurring.revise(next, command.fields().revision(), today)) {
      references.requireUsable(owner, next, changed);
      save.update(owner, recurring);
    }
    return RecurringView.from(recurring);
  }
}
