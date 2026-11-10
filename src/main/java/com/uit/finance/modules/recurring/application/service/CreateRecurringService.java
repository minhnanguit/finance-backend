package com.uit.finance.modules.recurring.application.service;

import com.uit.finance.modules.ledger.application.port.in.reference.LedgerReferenceQuery;
import com.uit.finance.modules.recurring.application.port.in.CreateRecurringUseCase;
import com.uit.finance.modules.recurring.application.port.in.RecurringView;
import com.uit.finance.modules.recurring.application.port.out.LoadRecurringPort;
import com.uit.finance.modules.recurring.application.port.out.RecurringCountPort;
import com.uit.finance.modules.recurring.application.port.out.RecurringWriteLockPort;
import com.uit.finance.modules.recurring.application.port.out.SaveRecurringPort;
import com.uit.finance.modules.recurring.domain.exception.RecurringLimitReachedException;
import com.uit.finance.modules.recurring.domain.exception.RecurringNotFoundException;
import com.uit.finance.modules.recurring.domain.exception.RevisionConflictException;
import com.uit.finance.modules.recurring.domain.model.Recurring;
import com.uit.finance.modules.recurring.domain.model.RecurringId;
import com.uit.finance.modules.recurring.domain.model.RecurringLimits;
import com.uit.finance.modules.recurring.domain.model.RecurringTerms;
import com.uit.finance.shared.kernel.UserId;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class CreateRecurringService implements CreateRecurringUseCase {

  private final RecurringWriteLockPort writeLock;
  private final LoadRecurringPort load;
  private final SaveRecurringPort save;
  private final RecurringCountPort count;
  private final RecurringReferences references;
  private final Clock clock;

  CreateRecurringService(
      RecurringWriteLockPort writeLock,
      LoadRecurringPort load,
      SaveRecurringPort save,
      RecurringCountPort count,
      LedgerReferenceQuery ledger,
      Clock clock) {
    this.writeLock = writeLock;
    this.load = load;
    this.save = save;
    this.count = count;
    this.references = new RecurringReferences(ledger);
    this.clock = clock;
  }

  @Override
  public RecurringView create(CreateRecurringCommand command) {
    UserId owner = command.userId();
    writeLock.lock(owner);
    RecurringId id = RecurringId.of(command.recurringId());
    LocalDate today = RecurringParser.today(clock);
    RecurringTerms terms = RecurringParser.terms(command.fields());

    // Bản của chính user đã có: tạo lại với revision 0 nghĩa là client chưa biết bản đó (RS8).
    load.find(owner, id)
        .ifPresent(
            existing -> {
              existing.requireNotDeleted();
              throw new RevisionConflictException(id.value());
            });

    Recurring recurring = Recurring.create(id, owner, terms, command.fields().revision(), today);
    references.requireUsable(owner, terms, terms.revisions());
    if (recurring.isActiveOn(today)
        && count.countActive(owner, today.minusDays(RecurringLimits.TODAY_TOLERANCE_DAYS))
            >= RecurringLimits.MAX_ACTIVE_PER_USER) {
      throw new RecurringLimitReachedException(RecurringLimits.MAX_ACTIVE_PER_USER);
    }
    // Bản của chính user đã được đọc dưới khoá ở trên: insert thua chỉ có thể là id của người khác.
    if (!save.insertIfAbsent(owner, recurring)) {
      throw new RecurringNotFoundException(id.value());
    }
    return RecurringView.from(recurring);
  }
}
