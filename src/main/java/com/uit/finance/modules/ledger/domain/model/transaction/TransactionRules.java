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
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Luật của giao dịch cần tới ví và danh mục thật (ADR-005 §3). */
final class TransactionRules {

  private TransactionRules() {}

  /**
   * @param previous bản trước khi sửa, {@code null} khi tạo mới. Dùng để chỉ chặn archive khi giao
   *     dịch được gắn mới vào ví hoặc danh mục đó.
   */
  static void check(
      UserId owner,
      TransactionDetails next,
      TransactionReferences references,
      LocalDate today,
      @Nullable TransactionDetails previous) {
    checkDate(next.occurredOn(), today);

    Account account = references.account();
    References.requireVisible(owner, account);
    References.requireAttachable(
        account, previous == null || !previous.accountId().equals(next.accountId()));
    if (!next.amount().currency().equals(account.currency())) {
      throw new TransactionCurrencyMismatchException();
    }

    if (next.type().isTransfer()) {
      checkTransfer(owner, next, account, references.counterAccount(), previous);
    } else {
      checkCategory(owner, next, references.category(), previous);
    }
  }

  private static void checkDate(LocalDate occurredOn, LocalDate today) {
    if (occurredOn.isBefore(EARLIEST_DATE) || occurredOn.isAfter(LedgerLimits.latestDate(today))) {
      throw new InvalidFieldException("occurredOn");
    }
  }

  private static void checkTransfer(
      UserId owner,
      TransactionDetails next,
      Account account,
      @Nullable Account counterAccount,
      @Nullable TransactionDetails previous) {
    Account counter = Ensure.notNull(counterAccount, "counterAccount");
    References.requireVisible(owner, counter);
    References.requireAttachable(
        counter,
        previous == null || !Objects.equals(previous.counterAccountId(), next.counterAccountId()));
    if (!counter.currency().equals(account.currency())) {
      throw new InvalidTransferException("both accounts must use the same currency");
    }
  }

  private static void checkCategory(
      UserId owner,
      TransactionDetails next,
      @Nullable Category referenced,
      @Nullable TransactionDetails previous) {
    Category category = Ensure.notNull(referenced, "category");
    References.requireVisible(owner, category);
    References.requireAttachable(
        category, previous == null || !Objects.equals(previous.categoryId(), next.categoryId()));
    if (category.getKind() != next.type().categoryKind()) {
      throw new CategoryKindMismatchException();
    }
  }
}
