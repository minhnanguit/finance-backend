package com.uit.finance.modules.recurring.application.service;

import com.uit.finance.modules.ledger.application.port.in.reference.AccountRef;
import com.uit.finance.modules.ledger.application.port.in.reference.CategoryRef;
import com.uit.finance.modules.ledger.application.port.in.reference.LedgerReferenceQuery;
import com.uit.finance.modules.recurring.domain.exception.InvalidRecurringAccountException;
import com.uit.finance.modules.recurring.domain.exception.InvalidRecurringCategoryException;
import com.uit.finance.modules.recurring.domain.exception.RecurringCurrencyMismatchException;
import com.uit.finance.modules.recurring.domain.exception.RecurringReferencePendingException;
import com.uit.finance.modules.recurring.domain.model.RecurrenceRevision;
import com.uit.finance.modules.recurring.domain.model.RecurringTerms;
import com.uit.finance.shared.kernel.UserId;
import java.util.Collection;

/**
 * Kiểm ví/danh mục của các phiên bản thêm hoặc sửa qua {@code ledger::reference} (ADR-008 R12,
 * RS13). Chỗ duy nhất của module biết hình dạng {@link AccountRef} và {@link CategoryRef}.
 *
 * <p>Archive không chặn (R13: app tự ngừng sinh). Không thấy (chưa tới hoặc của user khác) là
 * {@code reference_pending}, hai trường hợp không phân biệt được (B2).
 */
final class RecurringReferences {

  private final LedgerReferenceQuery ledger;

  RecurringReferences(LedgerReferenceQuery ledger) {
    this.ledger = ledger;
  }

  void requireUsable(UserId owner, RecurringTerms terms, Collection<RecurrenceRevision> revisions) {
    for (RecurrenceRevision revision : revisions) {
      AccountRef account =
          ledger
              .findAccount(owner, revision.accountId())
              .orElseThrow(() -> new RecurringReferencePendingException(revision.accountId()));
      CategoryRef category =
          ledger
              .findCategory(owner, revision.categoryId())
              .orElseThrow(() -> new RecurringReferencePendingException(revision.categoryId()));
      if (account.deleted()) {
        throw new InvalidRecurringAccountException(account.id());
      }
      if (!account.currency().equals(terms.currency().getCurrencyCode())) {
        throw new RecurringCurrencyMismatchException(account.id());
      }
      if (category.deleted() || !category.kind().equals(terms.type().name())) {
        throw new InvalidRecurringCategoryException(category.id());
      }
    }
  }
}
