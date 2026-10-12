package com.uit.finance.modules.ledger.application.service.account;

import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.ANN;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.BOB;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.NOW;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.TODAY;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.account;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.assertRejected;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.category;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.expense;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.refs;
import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.modules.ledger.application.port.in.account.AccountFields;
import com.uit.finance.modules.ledger.application.port.in.account.AccountView;
import com.uit.finance.modules.ledger.application.port.in.account.ArchiveAccountUseCase.ArchiveAccountCommand;
import com.uit.finance.modules.ledger.application.port.in.account.CreateAccountUseCase.CreateAccountCommand;
import com.uit.finance.modules.ledger.application.port.in.account.DeleteAccountUseCase.DeleteAccountCommand;
import com.uit.finance.modules.ledger.application.port.in.account.UpdateAccountUseCase.UpdateAccountCommand;
import com.uit.finance.modules.ledger.application.service.support.LedgerFakes;
import com.uit.finance.modules.ledger.domain.exception.CurrencyLockedException;
import com.uit.finance.modules.ledger.domain.exception.EntityDeletedException;
import com.uit.finance.modules.ledger.domain.exception.InUseException;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.modules.ledger.domain.exception.LimitExceededException;
import com.uit.finance.modules.ledger.domain.model.account.Account;
import com.uit.finance.modules.ledger.domain.model.account.AccountDetails;
import com.uit.finance.modules.ledger.domain.model.account.AccountId;
import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.modules.ledger.domain.model.category.CategoryKind;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerLimits;
import com.uit.finance.modules.ledger.domain.model.transaction.Transaction;
import com.uit.finance.modules.ledger.domain.model.transaction.TransactionId;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountServicesTest {

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private LedgerFakes.Store store;
  private CreateAccountService create;
  private UpdateAccountService update;
  private ArchiveAccountService archive;
  private DeleteAccountService delete;

  @BeforeEach
  void setUp() {
    store = new LedgerFakes.Store();
    create = new CreateAccountService(store, store, store);
    update = new UpdateAccountService(store, store, store);
    archive = new ArchiveAccountService(store, store, clock);
    delete = new DeleteAccountService(store, store, store, clock);
  }

  private static AccountFields fields(String name, String currency) {
    return new AccountFields(name, "BANK", currency, 1_000_000, 0);
  }

  private void referenceFromATransaction(Account account) {
    Category food = category(account.getOwner(), CategoryKind.EXPENSE);
    store.put(food);
    store.put(
        Transaction.record(
            new TransactionId(UUID.randomUUID()),
            account.getOwner(),
            expense(account, food).build(),
            refs(account, food),
            TODAY,
            NOW));
  }

  @Test
  @DisplayName("tạo ví với id do client sinh")
  void createsAccount() {
    UUID id = UUID.randomUUID();

    AccountView view =
        create.create(new CreateAccountCommand(ANN, id, fields(" Vietcombank ", "VND")));

    assertThat(view.id()).isEqualTo(id);
    assertThat(view.name()).isEqualTo("Vietcombank");
    assertThat(view.openingBalanceMinor()).isEqualTo(1_000_000);
    assertThat(store.find(ANN, new AccountId(id))).isPresent();
  }

  @Test
  @DisplayName("gửi lại cùng id là no-op, trả bản đang có")
  void createIsIdempotent() {
    UUID id = UUID.randomUUID();
    create.create(new CreateAccountCommand(ANN, id, fields("Lần 1", "VND")));

    AccountView again = create.create(new CreateAccountCommand(ANN, id, fields("Lần 2", "VND")));

    assertThat(again.name()).isEqualTo("Lần 1");
    assertThat(store.inserts).isEqualTo(1);
  }

  @Test
  @DisplayName("id đã thuộc user khác: not_found, ví của họ không bị ghi đè (B1)")
  void createNeverOverwritesAnotherUsersRecord() {
    Account bobs = account(BOB, "VND");
    store.put(bobs);

    assertRejected(
        () ->
            create.create(
                new CreateAccountCommand(ANN, bobs.getId().value(), fields("Chiếm", "USD"))),
        LedgerNotFoundException.CODE);
    assertThat(store.account(bobs).getOwner()).isEqualTo(BOB);
    assertThat(store.account(bobs).currency().getCurrencyCode()).isEqualTo("VND");
  }

  @Test
  @DisplayName("thua race với request song song của chính user: trả bản của request kia")
  void concurrentOwnInsertIsAdopted() {
    UUID id = UUID.randomUUID();
    store.beforeInsert =
        () ->
            store.put(
                Account.open(
                    new AccountId(id), ANN, AccountDetails.parse("Bản kia", "CASH", "VND", 0, 0)));

    AccountView view = create.create(new CreateAccountCommand(ANN, id, fields("Bản này", "VND")));

    assertThat(view.name()).isEqualTo("Bản kia");
  }

  @Test
  @DisplayName("tối đa 50 ví mỗi user, ví của user khác không tính")
  void accountLimitPerUser() {
    for (int i = 0; i < LedgerLimits.MAX_ACCOUNTS_PER_USER; i++) {
      store.put(account(ANN, "VND"));
      store.put(account(BOB, "VND"));
    }

    assertRejected(
        () -> create.create(new CreateAccountCommand(ANN, UUID.randomUUID(), fields("51", "VND"))),
        LimitExceededException.CODE);
  }

  @Test
  @DisplayName("sửa ví của user khác: not_found, ví đó không đổi")
  void cannotUpdateAnotherUsersAccount() {
    Account bobs = account(BOB, "VND");
    store.put(bobs);

    assertRejected(
        () ->
            update.update(
                new UpdateAccountCommand(ANN, bobs.getId().value(), fields("Chiếm", "VND"))),
        LedgerNotFoundException.CODE);
    assertThat(store.updates).isZero();
  }

  @Test
  @DisplayName("đổi tiền tệ ví đã có giao dịch: currency_locked")
  void currencyLockedOnceUsed() {
    Account wallet = account(ANN, "VND");
    store.put(wallet);
    referenceFromATransaction(wallet);

    assertRejected(
        () ->
            update.update(
                new UpdateAccountCommand(ANN, wallet.getId().value(), fields("Ví", "USD"))),
        CurrencyLockedException.CODE);
  }

  @Test
  @DisplayName("sửa được lưu lại; gửi y nguyên thì không ghi")
  void updateSavesOnlyChanges() {
    UUID id = UUID.randomUUID();
    create.create(new CreateAccountCommand(ANN, id, fields("Ví", "VND")));

    update.update(new UpdateAccountCommand(ANN, id, fields("Ví lương", "VND")));
    update.update(new UpdateAccountCommand(ANN, id, fields("Ví lương", "VND")));

    assertThat(store.updates).isEqualTo(1);
    assertThat(store.find(ANN, new AccountId(id)).orElseThrow().getDetails().name().value())
        .isEqualTo("Ví lương");
  }

  @Test
  @DisplayName("archive được lưu, archive lần hai không ghi")
  void archiveIsPersistedOnce() {
    Account wallet = account(ANN, "VND");
    store.put(wallet);

    AccountView view =
        archive.setArchived(new ArchiveAccountCommand(ANN, wallet.getId().value(), true));
    archive.setArchived(new ArchiveAccountCommand(ANN, wallet.getId().value(), true));

    assertThat(view.archivedAt()).isEqualTo(NOW);
    assertThat(store.account(wallet).isArchived()).isTrue();
    assertThat(store.updates).isEqualTo(1);
  }

  @Test
  @DisplayName("xoá ví đang có giao dịch: in_use")
  void cannotDeleteUsedAccount() {
    Account wallet = account(ANN, "VND");
    store.put(wallet);
    referenceFromATransaction(wallet);

    assertRejected(
        () -> delete.delete(new DeleteAccountCommand(ANN, wallet.getId().value())),
        InUseException.CODE);
  }

  @Test
  @DisplayName("xoá xong, tạo lại cùng id là deleted: xoá luôn thắng (S4)")
  void deleteWinsOverRecreate() {
    UUID id = UUID.randomUUID();
    create.create(new CreateAccountCommand(ANN, id, fields("Ví", "VND")));

    AccountView tombstone = delete.delete(new DeleteAccountCommand(ANN, id));

    assertThat(tombstone.deleted()).isTrue();
    assertRejected(
        () -> create.create(new CreateAccountCommand(ANN, id, fields("Ví", "VND"))),
        EntityDeletedException.CODE);
    assertRejected(
        () -> update.update(new UpdateAccountCommand(ANN, id, fields("Ví", "VND"))),
        EntityDeletedException.CODE);
  }
}
