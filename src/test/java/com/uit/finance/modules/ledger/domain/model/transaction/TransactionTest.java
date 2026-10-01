package com.uit.finance.modules.ledger.domain.model.transaction;

import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.ANN;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.BOB;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.NOW;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.TODAY;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.account;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.archived;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.assertInvalidField;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.assertRejected;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.category;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.deleted;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.expense;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.refs;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.transfer;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.transferRefs;
import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.modules.ledger.domain.event.TransactionDeleted;
import com.uit.finance.modules.ledger.domain.event.TransactionRecorded;
import com.uit.finance.modules.ledger.domain.event.TransactionUpdated;
import com.uit.finance.modules.ledger.domain.exception.ArchivedException;
import com.uit.finance.modules.ledger.domain.exception.CategoryKindMismatchException;
import com.uit.finance.modules.ledger.domain.exception.EntityDeletedException;
import com.uit.finance.modules.ledger.domain.exception.InvalidTransferException;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.modules.ledger.domain.exception.TransactionCurrencyMismatchException;
import com.uit.finance.modules.ledger.domain.model.account.Account;
import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.modules.ledger.domain.model.category.CategoryKind;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerLimits;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransactionTest {

  private final Account wallet = account(ANN, "VND");
  private final Category food = category(ANN, CategoryKind.EXPENSE);

  private static Transaction record(TransactionDetails details, TransactionReferences references) {
    return Transaction.record(
        new TransactionId(UUID.randomUUID()), ANN, details, references, TODAY, NOW);
  }

  private Transaction recordedExpense() {
    Transaction transaction = record(expense(wallet, food).build(), refs(wallet, food));
    transaction.pullDomainEvents();
    return transaction;
  }

  @Test
  @DisplayName("ghi giao dịch phát đúng một TransactionRecorded mang id")
  void recordPublishesEvent() {
    Transaction transaction = record(expense(wallet, food).build(), refs(wallet, food));

    assertThat(transaction.pullDomainEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            TransactionRecorded.class,
            event -> {
              assertThat(event.transactionId()).isEqualTo(transaction.getId().value());
              assertThat(event.userId()).isEqualTo(ANN.value());
              assertThat(event.accountId()).isEqualTo(wallet.getId().value());
              assertThat(event.occurredOn()).isEqualTo(TODAY);
            });
  }

  @Test
  @DisplayName("ngày giao dịch từ 01/01/2000 tới hôm nay + 1 năm")
  void dateBounds() {
    assertThat(record(expense(wallet, food).on(TODAY.plusYears(1)).build(), refs(wallet, food)))
        .isNotNull();
    assertThat(
            record(
                expense(wallet, food).on(LedgerLimits.EARLIEST_DATE).build(), refs(wallet, food)))
        .isNotNull();
    assertInvalidField(
        () ->
            record(
                expense(wallet, food).on(TODAY.plusYears(1).plusDays(1)).build(),
                refs(wallet, food)),
        "occurredOn");
    assertInvalidField(
        () ->
            record(
                expense(wallet, food).on(LedgerLimits.EARLIEST_DATE.minusDays(1)).build(),
                refs(wallet, food)),
        "occurredOn");
  }

  @Test
  @DisplayName("loại danh mục phải khớp loại giao dịch")
  void categoryKindMustMatch() {
    Category salary = category(ANN, CategoryKind.INCOME);

    assertRejected(
        () ->
            record(
                expense(wallet, food).category(salary.getId().value()).build(),
                refs(wallet, salary)),
        CategoryKindMismatchException.CODE);
  }

  @Test
  @DisplayName("tiền tệ giao dịch phải bằng tiền tệ ví")
  void currencyMustMatchAccount() {
    assertRejected(
        () -> record(expense(wallet, food).currency("USD").build(), refs(wallet, food)),
        TransactionCurrencyMismatchException.CODE);
  }

  @Test
  @DisplayName("chuyển tiền giữa 2 ví khác tiền tệ bị chặn")
  void transferNeedsSameCurrency() {
    Account dollars = account(ANN, "USD");

    assertRejected(
        () -> record(transfer(wallet, dollars).build(), transferRefs(wallet, dollars)),
        InvalidTransferException.CODE);
  }

  @Test
  @DisplayName("ví, ví đến, danh mục của user khác hoặc đã xoá đều là not_found")
  void referencesMustBeVisible() {
    Account foreign = account(BOB, "VND");
    Account gone = deleted(account(ANN, "VND"));
    Category foreignCategory = category(BOB, CategoryKind.EXPENSE);

    assertRejected(
        () -> record(expense(foreign, food).build(), refs(foreign, food)),
        LedgerNotFoundException.CODE);
    assertRejected(
        () -> record(expense(gone, food).build(), refs(gone, food)), LedgerNotFoundException.CODE);
    assertRejected(
        () -> record(transfer(wallet, foreign).build(), transferRefs(wallet, foreign)),
        LedgerNotFoundException.CODE);
    assertRejected(
        () -> record(expense(wallet, foreignCategory).build(), refs(wallet, foreignCategory)),
        LedgerNotFoundException.CODE);
  }

  @Test
  @DisplayName("không gắn giao dịch mới vào ví hay danh mục đã archive")
  void cannotAttachToArchived() {
    Account old = archived(account(ANN, "VND"));
    Category oldCategory = category(ANN, CategoryKind.EXPENSE);
    oldCategory.changeArchived(true, NOW);

    assertRejected(
        () -> record(expense(old, food).build(), refs(old, food)), ArchivedException.CODE);
    assertRejected(
        () -> record(expense(wallet, oldCategory).build(), refs(wallet, oldCategory)),
        ArchivedException.CODE);
  }

  @Test
  @DisplayName("giao dịch cũ trong ví đã archive vẫn sửa được ghi chú")
  void existingTransactionInArchivedAccountStaysEditable() {
    Transaction transaction = recordedExpense();
    archived(wallet);

    boolean changed =
        transaction.revise(
            expense(wallet, food).note("sửa ghi chú").build(),
            () -> refs(wallet, food),
            TODAY,
            NOW);

    assertThat(changed).isTrue();
    assertThat(transaction.pullDomainEvents())
        .singleElement()
        .isInstanceOf(TransactionUpdated.class);
  }

  @Test
  @DisplayName("chuyển giao dịch sang ví đã archive thì bị chặn")
  void movingIntoArchivedAccountIsRejected() {
    Transaction transaction = recordedExpense();
    Account old = archived(account(ANN, "VND"));

    assertRejected(
        () -> transaction.revise(expense(old, food).build(), () -> refs(old, food), TODAY, NOW),
        ArchivedException.CODE);
  }

  @Test
  @DisplayName("gửi lại y nguyên: không đổi, không event, không load ví/danh mục")
  void identicalReviseIsNoOp() {
    Transaction transaction = recordedExpense();

    boolean changed =
        transaction.revise(
            expense(wallet, food).build(),
            () -> {
              throw new AssertionError("references must not be loaded");
            },
            TODAY,
            NOW);

    assertThat(changed).isFalse();
    assertThat(transaction.pullDomainEvents()).isEmpty();
  }

  @Test
  @DisplayName("xoá phát TransactionDeleted một lần; sửa sau khi xoá là deleted (S4)")
  void deleteWins() {
    Transaction transaction = recordedExpense();

    assertThat(transaction.delete(NOW)).isTrue();
    assertThat(transaction.delete(NOW)).isFalse();
    assertThat(transaction.pullDomainEvents())
        .singleElement()
        .isInstanceOf(TransactionDeleted.class);
    assertRejected(
        () ->
            transaction.revise(
                expense(wallet, food).amount(1).build(), () -> refs(wallet, food), TODAY, NOW),
        EntityDeletedException.CODE);
  }
}
