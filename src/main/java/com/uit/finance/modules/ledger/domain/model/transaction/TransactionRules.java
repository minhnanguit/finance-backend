package com.uit.finance.modules.ledger.domain.model.transaction;

import static com.uit.finance.modules.ledger.domain.model.shared.LedgerLimits.EARLIEST_DATE;

import com.uit.finance.modules.ledger.domain.exception.CategoryKindMismatchException;
import com.uit.finance.modules.ledger.domain.exception.InvalidFieldException;
import com.uit.finance.modules.ledger.domain.exception.InvalidTransferException;
import com.uit.finance.modules.ledger.domain.exception.TransactionCurrencyMismatchException;
import com.uit.finance.modules.ledger.domain.model.account.Account;
import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerLimits;
import com.uit.finance.modules.ledger.domain.model.shared.References;
import com.uit.finance.shared.kernel.Ensure;
import com.uit.finance.shared.kernel.UserId;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Luật của giao dịch cần tới ví và danh mục thật (ADR-005 §3). Ví/danh mục đã archive vẫn trỏ tới
 * được, xem {@link References}.
 */
final class TransactionRules {

  private TransactionRules() {}

  static void check(
      UserId owner, TransactionDetails next, TransactionReferences references, LocalDate today) {
    checkDate(next.occurredOn(), today);

    Account account = references.account();
    References.requireVisible(owner, account);
    if (!next.amount().currency().equals(account.currency())) {
      throw new TransactionCurrencyMismatchException();
    }

    if (next.type().isTransfer()) {
      checkTransfer(owner, account, references.counterAccount());
    } else {
      checkCategory(owner, next, references.category());
    }
  }

  private static void checkDate(LocalDate occurredOn, LocalDate today) {
    if (occurredOn.isBefore(EARLIEST_DATE) || occurredOn.isAfter(LedgerLimits.latestDate(today))) {
      throw new InvalidFieldException("occurredOn");
    }
  }

  private static void checkTransfer(
      UserId owner, Account account, @Nullable Account counterAccount) {
    Account counter = Ensure.notNull(counterAccount, "counterAccount");
    References.requireVisible(owner, counter);
    if (!counter.currency().equals(account.currency())) {
      throw new InvalidTransferException("both accounts must use the same currency");
    }
  }

  private static void checkCategory(
      UserId owner, TransactionDetails next, @Nullable Category referenced) {
    Category category = Ensure.notNull(referenced, "category");
    References.requireVisible(owner, category);
    if (category.getKind() != next.type().categoryKind()) {
      throw new CategoryKindMismatchException();
    }
  }
}
