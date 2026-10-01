package com.uit.finance.modules.ledger.application.service;

import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.ANN;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.BOB;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.NOW;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.TODAY;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.account;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.assertRejected;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.category;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.deleted;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.expense;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.refs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.uit.finance.modules.ledger.application.port.in.DeleteTransactionUseCase.DeleteTransactionCommand;
import com.uit.finance.modules.ledger.application.port.in.RecordTransactionUseCase.RecordTransactionCommand;
import com.uit.finance.modules.ledger.application.port.in.TransactionFields;
import com.uit.finance.modules.ledger.application.port.in.TransactionView;
import com.uit.finance.modules.ledger.application.port.in.UpdateTransactionUseCase.UpdateTransactionCommand;
import com.uit.finance.modules.ledger.domain.event.TransactionDeleted;
import com.uit.finance.modules.ledger.domain.event.TransactionRecorded;
import com.uit.finance.modules.ledger.domain.event.TransactionUpdated;
import com.uit.finance.modules.ledger.domain.exception.EntityDeletedException;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.modules.ledger.domain.exception.ReferencePendingException;
import com.uit.finance.modules.ledger.domain.model.Account;
import com.uit.finance.modules.ledger.domain.model.Category;
import com.uit.finance.modules.ledger.domain.model.CategoryKind;
import com.uit.finance.modules.ledger.domain.model.Transaction;
import com.uit.finance.modules.ledger.domain.model.TransactionId;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransactionServicesTest {

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private LedgerFakes.Store store;
  private LedgerFakes.RecordingEvents events;
  private RecordTransactionService record;
  private UpdateTransactionService update;
  private DeleteTransactionService delete;

  private Account wallet;
  private Category food;

  @BeforeEach
  void setUp() {
    store = new LedgerFakes.Store();
    events = new LedgerFakes.RecordingEvents();
    TransactionReferenceResolver references = new TransactionReferenceResolver(store, store);
    record = new RecordTransactionService(store, store, references, events, clock);
    update = new UpdateTransactionService(store, store, references, events, clock);
    delete = new DeleteTransactionService(store, store, events, clock);

    wallet = account(ANN, "VND");
    food = category(ANN, CategoryKind.EXPENSE);
    store.put(wallet);
    store.put(food);
  }

  private TransactionView recordExpense(UUID id, TransactionFields fields) {
    return record.record(new RecordTransactionCommand(ANN, id, fields));
  }

  @Test
  @DisplayName("ghi giao dịch: lưu và phát một TransactionRecorded")
  void recordsAndPublishes() {
    UUID id = UUID.randomUUID();

    TransactionView view = recordExpense(id, expense(wallet, food).amount(55_000).fields());

    assertThat(view.amountMinor()).isEqualTo(55_000);
    assertThat(store.find(ANN, new TransactionId(id))).isPresent();
    assertThat(events.published).singleElement().isInstanceOf(TransactionRecorded.class);
  }

  @Test
  @DisplayName("gửi lại cùng id: một dòng, một event")
  void retryDoesNotDuplicate() {
    UUID id = UUID.randomUUID();

    recordExpense(id, expense(wallet, food).fields());
    recordExpense(id, expense(wallet, food).fields());

    assertThat(store.inserts).isEqualTo(1);
    assertThat(events.published).hasSize(1);
  }

  @Test
  @DisplayName("ví chưa tới và ví của user khác trả y hệt nhau (B2)")
  void missingAndForeignAccountsLookIdentical() {
    Account bobs = account(BOB, "VND");
    TransactionFields fields = expense(bobs, food).fields();
    UUID id = UUID.randomUUID();

    Throwable missing = catchThrowable(() -> recordExpense(id, fields));
    store.put(bobs);
    Throwable foreign = catchThrowable(() -> recordExpense(id, fields));

    assertThat(missing).isInstanceOf(ReferencePendingException.class);
    assertThat(foreign)
        .isInstanceOf(ReferencePendingException.class)
        .hasMessage(missing.getMessage());
    assertThat(events.published).isEmpty();
  }

  @Test
  @DisplayName("trỏ tới ví của chính mình đã xoá: not_found, không RETRY mãi")
  void ownDeletedAccountIsFinal() {
    Account gone = deleted(account(ANN, "VND"));
    store.put(gone);

    assertRejected(
        () -> recordExpense(UUID.randomUUID(), expense(gone, food).fields()),
        LedgerNotFoundException.CODE);
  }

  @Test
  @DisplayName("id giao dịch của user khác: not_found, giao dịch của họ không đổi (B1)")
  void cannotHijackAnotherUsersTransactionId() {
    Account bobsWallet = account(BOB, "VND");
    Category bobsFood = category(BOB, CategoryKind.EXPENSE);
    Transaction bobs =
        Transaction.record(
            new TransactionId(UUID.randomUUID()),
            BOB,
            expense(bobsWallet, bobsFood).amount(10_000).build(),
            refs(bobsWallet, bobsFood),
            TODAY,
            NOW);
    store.put(bobs);
    UUID bobsId = bobs.getId().value();

    assertRejected(
        () -> recordExpense(bobsId, expense(wallet, food).amount(1).fields()),
        LedgerNotFoundException.CODE);
    assertRejected(
        () ->
            update.update(
                new UpdateTransactionCommand(
                    ANN, bobsId, expense(wallet, food).amount(1).fields())),
        LedgerNotFoundException.CODE);
    assertRejected(
        () -> delete.delete(new DeleteTransactionCommand(ANN, bobsId)),
        LedgerNotFoundException.CODE);
    assertThat(store.transactions.get(bobsId).getDetails().amount().amountMinor())
        .isEqualTo(10_000);
    assertThat(store.transactions.get(bobsId).isDeleted()).isFalse();
  }

  @Test
  @DisplayName("sửa: lưu và phát TransactionUpdated; gửi y nguyên thì không ghi, không event")
  void updateSavesAndPublishesOnlyOnChange() {
    UUID id = UUID.randomUUID();
    recordExpense(id, expense(wallet, food).fields());
    events.published.clear();

    update.update(
        new UpdateTransactionCommand(ANN, id, expense(wallet, food).amount(70_000).fields()));
    update.update(
        new UpdateTransactionCommand(ANN, id, expense(wallet, food).amount(70_000).fields()));

    assertThat(store.updates).isEqualTo(1);
    assertThat(
            store
                .find(ANN, new TransactionId(id))
                .orElseThrow()
                .getDetails()
                .amount()
                .amountMinor())
        .isEqualTo(70_000);
    assertThat(events.published).singleElement().isInstanceOf(TransactionUpdated.class);
  }

  @Test
  @DisplayName(
      "xoá: tombstone + một TransactionDeleted; xoá lần hai không làm gì; sửa sau đó là deleted")
  void deleteIsFinal() {
    UUID id = UUID.randomUUID();
    recordExpense(id, expense(wallet, food).fields());
    events.published.clear();

    TransactionView tombstone = delete.delete(new DeleteTransactionCommand(ANN, id));
    delete.delete(new DeleteTransactionCommand(ANN, id));

    assertThat(tombstone.deleted()).isTrue();
    assertThat(store.updates).isEqualTo(1);
    assertThat(events.published).singleElement().isInstanceOf(TransactionDeleted.class);
    assertRejected(
        () ->
            update.update(
                new UpdateTransactionCommand(ANN, id, expense(wallet, food).amount(1).fields())),
        EntityDeletedException.CODE);
  }
}
