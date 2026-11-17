package com.uit.finance.modules.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.finance.TestIdentityProvider;
import com.uit.finance.TestcontainersConfiguration;
import com.uit.finance.modules.ledger.application.port.in.account.AccountFields;
import com.uit.finance.modules.ledger.application.port.in.account.CreateAccountUseCase;
import com.uit.finance.modules.ledger.application.port.in.account.CreateAccountUseCase.CreateAccountCommand;
import com.uit.finance.modules.ledger.application.port.in.account.DeleteAccountUseCase;
import com.uit.finance.modules.ledger.application.port.in.account.DeleteAccountUseCase.DeleteAccountCommand;
import com.uit.finance.modules.ledger.application.port.in.account.GetBalancesUseCase;
import com.uit.finance.modules.ledger.application.port.in.account.GetBalancesUseCase.AccountBalance;
import com.uit.finance.modules.ledger.application.port.in.account.GetBalancesUseCase.CurrencyTotal;
import com.uit.finance.modules.ledger.application.port.in.account.UpdateAccountUseCase;
import com.uit.finance.modules.ledger.application.port.in.account.UpdateAccountUseCase.UpdateAccountCommand;
import com.uit.finance.modules.ledger.application.port.in.category.CategoryFields;
import com.uit.finance.modules.ledger.application.port.in.category.CreateCategoryUseCase;
import com.uit.finance.modules.ledger.application.port.in.category.CreateCategoryUseCase.CreateCategoryCommand;
import com.uit.finance.modules.ledger.application.port.in.category.DeleteCategoryUseCase;
import com.uit.finance.modules.ledger.application.port.in.category.DeleteCategoryUseCase.DeleteCategoryCommand;
import com.uit.finance.modules.ledger.application.port.in.category.SeedDefaultCategoriesUseCase;
import com.uit.finance.modules.ledger.application.port.in.transaction.DeleteTransactionUseCase;
import com.uit.finance.modules.ledger.application.port.in.transaction.DeleteTransactionUseCase.DeleteTransactionCommand;
import com.uit.finance.modules.ledger.application.port.in.transaction.RecordTransactionUseCase;
import com.uit.finance.modules.ledger.application.port.in.transaction.RecordTransactionUseCase.RecordTransactionCommand;
import com.uit.finance.modules.ledger.application.port.in.transaction.TransactionFields;
import com.uit.finance.modules.ledger.domain.exception.CurrencyLockedException;
import com.uit.finance.modules.ledger.domain.exception.InUseException;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import com.uit.finance.modules.ledger.domain.model.category.TemplateKey;
import com.uit.finance.shared.kernel.UserId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Ledger trên Postgres thật: Flyway V4/V5, {@code ddl-auto=validate}, SQL của adapter, ràng buộc DB
 * và {@code change_seq}. Mỗi test dùng user mới nên không cần dọn dữ liệu.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LedgerPersistenceIT {

  private static final LocalDate TODAY = LocalDate.now(java.time.ZoneOffset.UTC);

  @Autowired JdbcClient jdbc;
  @Autowired CreateAccountUseCase createAccount;
  @Autowired UpdateAccountUseCase updateAccount;
  @Autowired DeleteAccountUseCase deleteAccount;
  @Autowired GetBalancesUseCase balances;
  @Autowired CreateCategoryUseCase createCategory;
  @Autowired DeleteCategoryUseCase deleteCategory;
  @Autowired SeedDefaultCategoriesUseCase seed;
  @Autowired RecordTransactionUseCase record;
  @Autowired DeleteTransactionUseCase deleteTransaction;

  private UserId ann;
  private UserId bob;

  @DynamicPropertySource
  static void identityProvider(DynamicPropertyRegistry registry) {
    TestIdentityProvider idp = TestIdentityProvider.instance();
    registry.add("app.security.issuer-uri", () -> TestIdentityProvider.ISSUER);
    registry.add("app.security.jwk-set-uri", idp::jwkSetUri);
    registry.add("app.security.audience", () -> TestIdentityProvider.AUDIENCE);
  }

  @BeforeEach
  void freshUsers() {
    ann = UserId.newId();
    bob = UserId.newId();
  }

  // ---- helpers ----

  private UUID account(UserId owner, String name, long opening) {
    UUID id = UUID.randomUUID();
    createAccount.create(
        new CreateAccountCommand(owner, id, new AccountFields(name, "BANK", "VND", opening, 0)));
    return id;
  }

  private UUID category(UserId owner, String template) {
    seed.seed(owner);
    return CategoryId.forTemplate(owner, new TemplateKey(template)).value();
  }

  private UUID record(
      UserId owner,
      String type,
      UUID account,
      UUID counter,
      UUID category,
      long amount,
      String status) {
    UUID id = UUID.randomUUID();
    record.record(
        new RecordTransactionCommand(
            owner,
            id,
            new TransactionFields(
                type, status, account, counter, amount, "VND", category, TODAY, null, null, null)));
    return id;
  }

  private UUID expense(UserId owner, UUID account, UUID category, long amount) {
    return record(owner, "EXPENSE", account, null, category, amount, "CONFIRMED");
  }

  /** Mọi {@code change_seq} của user trên cả 3 bảng, tăng dần. */
  private List<Long> changeSeqs(UserId owner) {
    return jdbc.sql(
            """
            SELECT change_seq FROM accounts WHERE user_id = :u
            UNION ALL SELECT change_seq FROM categories WHERE user_id = :u
            UNION ALL SELECT change_seq FROM transactions WHERE user_id = :u
            ORDER BY 1
            """)
        .param("u", owner.value())
        .query(Long.class)
        .list();
  }

  private long changeSeqOf(String table, UUID id) {
    return jdbc.sql("SELECT change_seq FROM " + table + " WHERE id = :id")
        .param("id", id)
        .query(Long.class)
        .single();
  }

  private static List<Long> oneTo(long n) {
    return LongStream.rangeClosed(1, n).boxed().toList();
  }

  // ---- tests ----

  @Test
  @DisplayName("seed 2 lần: lần đầu 16 danh mục với change_seq 1..16, lần sau không tạo gì")
  void seedIsIdempotentAndGapless() {
    assertThat(seed.seed(ann)).isEqualTo(16);
    assertThat(seed.seed(ann)).isZero();

    assertThat(changeSeqs(ann)).isEqualTo(oneTo(16));
  }

  @Test
  @DisplayName("số dư tính bằng SQL: bỏ DRAFT, bỏ giao dịch đã xoá, bỏ dữ liệu user khác")
  void balancesFromSql() {
    UUID wallet = account(ann, "Ví", 100_000);
    UUID bank = account(ann, "Bank", 0);
    UUID food = category(ann, "food");
    UUID salary = category(ann, "salary");
    expense(ann, wallet, food, 30_000);
    record(ann, "EXPENSE", wallet, null, food, 999, "DRAFT");
    record(ann, "INCOME", wallet, null, salary, 50_000, "CONFIRMED");
    record(ann, "TRANSFER", wallet, bank, null, 20_000, "CONFIRMED");
    UUID removed = expense(ann, wallet, food, 7_000);
    deleteTransaction.delete(new DeleteTransactionCommand(ann, removed));
    expense(bob, account(bob, "Ví Bob", 0), category(bob, "food"), 1);

    var result = balances.balances(ann);

    assertThat(result.accounts())
        .containsExactlyInAnyOrder(
            new AccountBalance(wallet, 100_000, "VND", false),
            new AccountBalance(bank, 20_000, "VND", false));
    assertThat(result.totals()).containsExactly(new CurrencyTotal("VND", 120_000));
  }

  @Test
  @DisplayName("mỗi lần ghi lấy change_seq mới; gửi y nguyên không tốn số; xoá để lại tombstone")
  void everyWriteGetsAChangeSeq() {
    UUID wallet = account(ann, "Ví", 0);
    long created = changeSeqOf("accounts", wallet);

    updateAccount.update(
        new UpdateAccountCommand(ann, wallet, new AccountFields("Ví lương", "BANK", "VND", 0, 0)));
    long renamed = changeSeqOf("accounts", wallet);
    updateAccount.update(
        new UpdateAccountCommand(ann, wallet, new AccountFields("Ví lương", "BANK", "VND", 0, 0)));
    deleteAccount.delete(new DeleteAccountCommand(ann, wallet));

    assertThat(renamed).isEqualTo(created + 1);
    assertThat(changeSeqOf("accounts", wallet)).isEqualTo(renamed + 1);
    assertThat(
            jdbc.sql("SELECT deleted_at IS NOT NULL FROM accounts WHERE id = :id")
                .param("id", wallet)
                .query(Boolean.class)
                .single())
        .isTrue();
  }

  @Test
  @DisplayName("id của user khác: not_found, dòng của họ không đổi, và không tốn số của ai (B1)")
  void anotherUsersIdIsNeverOverwritten() {
    UUID annsWallet = account(ann, "Ví Ann", 0);

    assertThatThrownBy(
            () ->
                createAccount.create(
                    new CreateAccountCommand(
                        bob, annsWallet, new AccountFields("Chiếm", "CASH", "USD", 0, 0))))
        .isInstanceOf(LedgerNotFoundException.class);
    assertThatThrownBy(
            () ->
                updateAccount.update(
                    new UpdateAccountCommand(
                        bob, annsWallet, new AccountFields("Chiếm", "CASH", "VND", 0, 0))))
        .isInstanceOf(LedgerNotFoundException.class);

    var row =
        jdbc.sql("SELECT user_id, name, currency FROM accounts WHERE id = :id")
            .param("id", annsWallet)
            .query()
            .singleRow();
    assertThat(row.get("user_id")).isEqualTo(ann.value());
    assertThat(row.get("name")).isEqualTo("Ví Ann");
    assertThat(row.get("currency")).isEqualTo("VND");
    // Lần insert thua bị rollback cùng transaction, nên số đã lấy được trả lại.
    assertThat(changeSeqs(bob)).isEmpty();
    assertThat(account(bob, "Ví Bob", 0)).isNotNull();
    assertThat(changeSeqs(bob)).isEqualTo(oneTo(1));
  }

  @Test
  @DisplayName("truy vấn 'đang được dùng' chạy đúng trên DB thật")
  void usageQueries() {
    UUID wallet = account(ann, "Ví", 0);
    UUID food = category(ann, "food");
    expense(ann, wallet, food, 10_000);
    UUID coffee = UUID.randomUUID();
    createCategory.create(
        new CreateCategoryCommand(
            ann, coffee, new CategoryFields("EXPENSE", "Cà phê", food, null, null)));

    assertThatThrownBy(() -> deleteAccount.delete(new DeleteAccountCommand(ann, wallet)))
        .isInstanceOf(InUseException.class);
    assertThatThrownBy(() -> deleteCategory.delete(new DeleteCategoryCommand(ann, food)))
        .isInstanceOf(InUseException.class);
    assertThatThrownBy(
            () ->
                updateAccount.update(
                    new UpdateAccountCommand(
                        ann, wallet, new AccountFields("Ví", "BANK", "USD", 0, 0))))
        .isInstanceOf(CurrencyLockedException.class);
    assertThat(deleteCategory.delete(new DeleteCategoryCommand(ann, coffee)).deleted()).isTrue();
  }

  @Test
  @DisplayName("CHECK và FK ghép là lớp chặn thứ hai khi có ai ghi thẳng vào DB")
  void databaseConstraintsAreTheSecondLine() {
    UUID annsWallet = account(ann, "Ví Ann", 0);
    UUID annsFood = category(ann, "food");
    UUID bobsWallet = account(bob, "Ví Bob", 0);
    UUID bobsFood = category(bob, "food");

    // Số tiền 0
    assertThatThrownBy(() -> rawTransaction(ann, "EXPENSE", annsWallet, null, annsFood, 0))
        .isInstanceOf(DataIntegrityViolationException.class);
    // Chuyển tiền mà có danh mục
    assertThatThrownBy(
            () -> rawTransaction(ann, "TRANSFER", annsWallet, annsWallet, annsFood, 1_000))
        .isInstanceOf(DataIntegrityViolationException.class);
    // Giao dịch của Bob trỏ vào ví của Ann: FK (user_id, account_id) chặn
    assertThatThrownBy(() -> rawTransaction(bob, "EXPENSE", annsWallet, null, bobsFood, 1_000))
        .isInstanceOf(DataIntegrityViolationException.class);
    // Hợp lệ thì vào được
    rawTransaction(bob, "EXPENSE", bobsWallet, null, bobsFood, 1_000);
  }

  private void rawTransaction(
      UserId owner, String type, UUID account, UUID counter, UUID category, long amount) {
    jdbc.sql(
            """
            INSERT INTO transactions (id, user_id, type, status, account_id, counter_account_id,
                                      amount_minor, currency, category_id, occurred_on,
                                      change_seq, created_at, updated_at)
            VALUES (:id, :u, :type, 'CONFIRMED', :account, :counter, :amount, 'VND', :category,
                    CURRENT_DATE, :seq, now(), now())
            """)
        .param("id", UUID.randomUUID())
        .param("u", owner.value())
        .param("type", type)
        .param("account", account)
        .param("counter", counter, java.sql.Types.OTHER)
        .param("amount", amount)
        .param("category", category, java.sql.Types.OTHER)
        .param("seq", 1_000_000 + (long) (Math.random() * 1_000_000))
        .update();
  }

  @Test
  @DisplayName("20 lần ghi song song của cùng user: change_seq liền mạch, không lỗ, không trùng")
  void concurrentWritesGetAGaplessSequence() throws Exception {
    UUID wallet = account(ann, "Ví", 0);
    UUID food = category(ann, "food");
    int writes = 20;

    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Callable<UUID>> tasks = new ArrayList<>();
      for (int i = 0; i < writes; i++) {
        tasks.add(() -> expense(ann, wallet, food, 1_000));
      }
      for (Future<UUID> done : pool.invokeAll(tasks)) {
        done.get();
      }
    } finally {
      pool.shutdown();
    }

    // 1 ví + 16 danh mục mặc định + 20 giao dịch
    assertThat(changeSeqs(ann)).isEqualTo(oneTo(1 + 16 + writes));
  }
}
