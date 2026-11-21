package com.uit.finance.modules.recurring.domain.model;

import com.uit.finance.shared.kernel.UserId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/** Dữ liệu mẫu cho test recurring. "Hôm nay" là 10/11/2026 (UTC). */
public final class RecurringFixtures {

  public static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
  public static final UserId ANN = UserId.newId();
  public static final UserId BOB = UserId.newId();
  public static final Currency VND = Currency.getInstance("VND");

  private RecurringFixtures() {}

  public static RecurrenceRule monthly(int day) {
    return new RecurrenceRule(Frequency.MONTHLY, 1, null, null, day);
  }

  public static RecurrenceRevision revision(
      LocalDate effectiveFrom, UUID account, UUID category, long amount, RecurrenceRule rule) {
    return new RecurrenceRevision(
        UUID.randomUUID(),
        effectiveFrom,
        effectiveFrom,
        account,
        category,
        amount,
        null,
        rule,
        false);
  }

  public static RecurringTerms terms(List<RecurrenceRevision> revisions) {
    return new RecurringTerms(
        "Tiền nhà",
        RecurringType.EXPENSE,
        VND,
        revisions.getFirst().effectiveFrom(),
        null,
        2,
        revisions);
  }

  /** Bản sao của {@code terms} với timeline thêm {@code extra} ở cuối. */
  public static RecurringTerms withRevision(RecurringTerms terms, RecurrenceRevision extra) {
    List<RecurrenceRevision> next = new ArrayList<>(terms.revisions());
    next.add(extra);
    return new RecurringTerms(
        terms.name(),
        terms.type(),
        terms.currency(),
        terms.generationNotBefore(),
        terms.generationEndDate(),
        terms.remindDaysBefore(),
        next);
  }

  public static RecurringTerms withEnd(RecurringTerms terms, LocalDate end) {
    return new RecurringTerms(
        terms.name(),
        terms.type(),
        terms.currency(),
        terms.generationNotBefore(),
        end,
        terms.remindDaysBefore(),
        terms.revisions());
  }

  public static RecurringTerms withNotBefore(RecurringTerms terms, LocalDate notBefore) {
    return new RecurringTerms(
        terms.name(),
        terms.type(),
        terms.currency(),
        notBefore,
        terms.generationEndDate(),
        terms.remindDaysBefore(),
        terms.revisions());
  }
}
