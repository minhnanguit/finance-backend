package com.uit.finance.modules.budget.application.service;

import static com.uit.finance.modules.budget.domain.model.BudgetFixtures.ANN;
import static com.uit.finance.modules.budget.domain.model.BudgetFixtures.BOB;
import static com.uit.finance.modules.budget.domain.model.BudgetFixtures.NOW;
import static com.uit.finance.modules.budget.domain.model.BudgetFixtures.THIS_MONTH;
import static com.uit.finance.modules.budget.domain.model.BudgetFixtures.VND;
import static com.uit.finance.modules.budget.domain.model.BudgetFixtures.assertRejected;
import static com.uit.finance.modules.budget.domain.model.BudgetFixtures.budget;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.uit.finance.modules.budget.application.port.in.BudgetChangeFeedUseCase.BudgetChange;
import com.uit.finance.modules.budget.application.port.in.BudgetFields;
import com.uit.finance.modules.budget.application.port.in.BudgetView;
import com.uit.finance.modules.budget.application.port.in.CreateBudgetUseCase.CreateBudgetCommand;
import com.uit.finance.modules.budget.application.port.in.DeleteBudgetUseCase.DeleteBudgetCommand;
import com.uit.finance.modules.budget.application.port.in.ReconcileBudgetsOfCategoryUseCase.ReconcileBudgetsOfCategoryCommand;
import com.uit.finance.modules.budget.application.port.in.UpdateBudgetUseCase.UpdateBudgetCommand;
import com.uit.finance.modules.budget.domain.exception.BudgetDeletedException;
import com.uit.finance.modules.budget.domain.exception.BudgetLimitReachedException;
import com.uit.finance.modules.budget.domain.exception.BudgetNotFoundException;
import com.uit.finance.modules.budget.domain.exception.BudgetReferencePendingException;
import com.uit.finance.modules.budget.domain.exception.DuplicateBudgetException;
import com.uit.finance.modules.budget.domain.exception.ImmutableBudgetFieldException;
import com.uit.finance.modules.budget.domain.exception.InvalidBudgetCategoryException;
import com.uit.finance.modules.budget.domain.exception.InvalidBudgetFieldException;
import com.uit.finance.modules.budget.domain.model.BudgetLimits;
import com.uit.finance.modules.ledger.application.port.in.reference.CategoryRef;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BudgetServicesTest {

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private BudgetFakes.Store store;
  private BudgetFakes.Ledger ledger;
  private CreateBudgetService create;
  private UpdateBudgetService update;
  private DeleteBudgetService delete;
  private ReconcileBudgetsOfCategoryService reconcile;
  private BudgetChangeFeedService feed;

  @BeforeEach
  void setUp() {
    store = new BudgetFakes.Store();
    ledger = new BudgetFakes.Ledger();
    create = new CreateBudgetService(store, store, store, store, ledger, clock);
    update = new UpdateBudgetService(store, store, store, clock);
    delete = new DeleteBudgetService(store, store, store, clock);
    reconcile = new ReconcileBudgetsOfCategoryService(store, store, store, ledger, clock);
    feed = new BudgetChangeFeedService(store);
  }

  private static BudgetFields fields(UUID categoryId, String currency, long amount) {
    return new BudgetFields(categoryId, currency, amount, THIS_MONTH);
  }

  private BudgetView createFor(UUID categoryId, String currency) {
    store.releaseLocks();
    return create.create(
        new CreateBudgetCommand(ANN, UUID.randomUUID(), fields(categoryId, currency, 3_000_000)));
  }

  @Nested
  class Create {

    @Test
    @DisplayName("tạo ngân sách cho danh mục chi cấp 1")
    void createsForTopLevelExpense() {
      UUID food = ledger.expense(ANN);

      BudgetView view = createFor(food, "VND");

      assertThat(view.categoryId()).isEqualTo(food);
      assertThat(view.currency()).isEqualTo("VND");
      assertThat(view.amountMinor()).isEqualTo(3_000_000);
      assertThat(view.startMonth()).isEqualTo(THIS_MONTH);
      assertThat(store.inserts).isEqualTo(1);
    }

    @Test
    @DisplayName("gửi lại cùng id là no-op, trả bản đang có")
    void resendIsNoOp() {
      UUID food = ledger.expense(ANN);
      UUID id = UUID.randomUUID();
      create.create(new CreateBudgetCommand(ANN, id, fields(food, "VND", 3_000_000)));

      store.releaseLocks();
      BudgetView again =
          create.create(new CreateBudgetCommand(ANN, id, fields(food, "VND", 9_000_000)));

      assertThat(again.amountMinor()).isEqualTo(3_000_000);
      assertThat(store.inserts).isEqualTo(1);
    }

    @Test
    @DisplayName("tạo lại id đã xoá: budget.deleted (S4)")
    void recreatingDeletedIdConflicts() {
      UUID food = ledger.expense(ANN);
      UUID id = UUID.randomUUID();
      create.create(new CreateBudgetCommand(ANN, id, fields(food, "VND", 1)));
      delete.delete(new DeleteBudgetCommand(ANN, id));

      store.releaseLocks();
      assertRejected(
          () -> create.create(new CreateBudgetCommand(ANN, id, fields(food, "VND", 1))),
          BudgetDeletedException.CODE);
    }

    @Test
    @DisplayName("id của user khác: not_found, không ghi đè (B1)")
    void foreignIdIsNotFound() {
      UUID annsFood = ledger.expense(ANN);
      store.put(budget(BOB, ledger.expense(BOB), VND));
      UUID bobsId = store.rows.keySet().iterator().next();

      assertRejected(
          () -> create.create(new CreateBudgetCommand(ANN, bobsId, fields(annsFood, "VND", 1))),
          BudgetNotFoundException.CODE);
      assertThat(store.rows.get(bobsId).isOwnedBy(BOB)).isTrue();
      assertThat(store.inserts).isZero();
    }

    @Test
    @DisplayName("danh mục chưa tới và danh mục của user khác trả y hệt nhau (B2)")
    void missingAndForeignCategoryLookIdentical() {
      UUID bobsFood = ledger.expense(BOB);
      UUID unknown = UUID.randomUUID();

      Throwable foreign = catchThrowable(() -> createFor(bobsFood, "VND"));
      Throwable missing = catchThrowable(() -> createFor(unknown, "VND"));

      assertThat(foreign).isInstanceOf(BudgetReferencePendingException.class);
      assertThat(missing).isInstanceOf(BudgetReferencePendingException.class);
      assertThat(foreign.getMessage().replace(bobsFood.toString(), "?"))
          .isEqualTo(missing.getMessage().replace(unknown.toString(), "?"));
      assertThat(store.inserts).isZero();
    }

    @Test
    @DisplayName("danh mục thu, danh mục con, danh mục đã xoá: invalid_category")
    void rejectsInvalidCategories() {
      UUID food = ledger.expense(ANN);
      UUID salary = ledger.put(ANN, "INCOME", null, false, false);
      UUID coffee = ledger.put(ANN, "EXPENSE", food, false, false);
      UUID gone = ledger.put(ANN, "EXPENSE", null, false, true);

      assertRejected(() -> createFor(salary, "VND"), InvalidBudgetCategoryException.CODE);
      assertRejected(() -> createFor(coffee, "VND"), InvalidBudgetCategoryException.CODE);
      assertRejected(() -> createFor(gone, "VND"), InvalidBudgetCategoryException.CODE);
    }

    @Test
    @DisplayName("danh mục đã archive vẫn đặt được (G13)")
    void archivedCategoryIsAccepted() {
      UUID hidden = ledger.put(ANN, "EXPENSE", null, true, false);

      assertThat(createFor(hidden, "VND").categoryId()).isEqualTo(hidden);
    }

    @Test
    @DisplayName("cùng danh mục: cùng tiền tệ thì duplicate, khác tiền tệ thì được (G3)")
    void onePerCategoryAndCurrency() {
      UUID food = ledger.expense(ANN);
      createFor(food, "VND");

      assertRejected(() -> createFor(food, "VND"), DuplicateBudgetException.CODE);
      assertThat(createFor(food, "USD").currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("xoá rồi tạo lại bằng id mới thì được")
    void recreateAfterDeleteWithNewId() {
      UUID food = ledger.expense(ANN);
      BudgetView first = createFor(food, "VND");
      store.releaseLocks();
      delete.delete(new DeleteBudgetCommand(ANN, first.id()));

      assertThat(createFor(food, "VND").id()).isNotEqualTo(first.id());
    }

    @Test
    @DisplayName("field sai là invalid_field, không chạm ledger")
    void invalidFieldsAreRejectedFirst() {
      UUID food = ledger.expense(ANN);

      assertRejected(
          () ->
              create.create(
                  new CreateBudgetCommand(ANN, UUID.randomUUID(), fields(food, "VND", 0))),
          InvalidBudgetFieldException.CODE);
      assertRejected(
          () ->
              create.create(
                  new CreateBudgetCommand(
                      ANN,
                      UUID.randomUUID(),
                      new BudgetFields(food, "VND", 1, THIS_MONTH.plusMonths(2)))),
          InvalidBudgetFieldException.CODE);
      assertThat(ledger.singleLookups).isZero();
    }

    @Test
    @DisplayName("tối đa 100 ngân sách hợp lệ mỗi user")
    void limitOfValidBudgets() {
      for (int i = 0; i < BudgetLimits.MAX_BUDGETS_PER_USER; i++) {
        createFor(ledger.expense(ANN), "VND");
      }

      assertRejected(() -> createFor(ledger.expense(ANN), "VND"), BudgetLimitReachedException.CODE);
    }

    @Test
    @DisplayName("đã chạm 100 nhưng có danh mục vừa bị xoá: dọn rồi tạo được (GS8)")
    void staleBudgetsDoNotCountTowardsTheLimit() {
      UUID doomed = ledger.expense(ANN);
      BudgetView stale = createFor(doomed, "VND");
      for (int i = 1; i < BudgetLimits.MAX_BUDGETS_PER_USER; i++) {
        createFor(ledger.expense(ANN), "VND");
      }
      ledger.set(ANN, new CategoryRef(doomed, "EXPENSE", null, false, true));
      int batchBefore = ledger.batchLookups;

      BudgetView created = createFor(ledger.expense(ANN), "VND");

      assertThat(created).isNotNull();
      assertThat(store.rows.get(stale.id()).isDeleted()).isTrue();
      assertThat(store.countActive(ANN)).isEqualTo(BudgetLimits.MAX_BUDGETS_PER_USER);
      assertThat(ledger.batchLookups - batchBefore).as("một query cho cả lô").isEqualTo(1);
    }
  }

  @Nested
  class UpdateAndDelete {

    @Test
    @DisplayName("sửa số tiền")
    void updatesAmount() {
      UUID food = ledger.expense(ANN);
      BudgetView view = createFor(food, "VND");
      store.releaseLocks();

      BudgetView updated =
          update.update(new UpdateBudgetCommand(ANN, view.id(), fields(food, "VND", 5_000_000)));

      assertThat(updated.amountMinor()).isEqualTo(5_000_000);
      assertThat(store.updates).isEqualTo(1);
    }

    @Test
    @DisplayName("gửi lại y nguyên thì không ghi")
    void unchangedDoesNotWrite() {
      UUID food = ledger.expense(ANN);
      BudgetView view = createFor(food, "VND");
      store.releaseLocks();

      update.update(new UpdateBudgetCommand(ANN, view.id(), fields(food, "VND", 3_000_000)));

      assertThat(store.updates).isZero();
    }

    @Test
    @DisplayName("đổi tiền tệ: immutable_field, không ghi gì")
    void immutableFieldIsRejected() {
      UUID food = ledger.expense(ANN);
      BudgetView view = createFor(food, "VND");
      store.releaseLocks();

      assertRejected(
          () -> update.update(new UpdateBudgetCommand(ANN, view.id(), fields(food, "USD", 1))),
          ImmutableBudgetFieldException.CODE);
      assertThat(store.updates).isZero();
    }

    @Test
    @DisplayName("sửa bản đã xoá: budget.deleted, kể cả khi dữ liệu gửi lên sai")
    void updatingDeletedConflicts() {
      UUID food = ledger.expense(ANN);
      BudgetView view = createFor(food, "VND");
      store.releaseLocks();
      delete.delete(new DeleteBudgetCommand(ANN, view.id()));

      store.releaseLocks();
      assertRejected(
          () -> update.update(new UpdateBudgetCommand(ANN, view.id(), fields(food, "VND", 0))),
          BudgetDeletedException.CODE);
    }

    @Test
    @DisplayName("sửa hoặc xoá ngân sách của user khác: not_found, không đổi gì (B1)")
    void foreignBudgetIsNotFound() {
      UUID bobsFood = ledger.expense(BOB);
      store.put(budget(BOB, bobsFood, VND));
      UUID bobsId = store.rows.keySet().iterator().next();

      assertRejected(
          () -> update.update(new UpdateBudgetCommand(ANN, bobsId, fields(bobsFood, "VND", 1))),
          BudgetNotFoundException.CODE);
      assertRejected(
          () -> delete.delete(new DeleteBudgetCommand(ANN, bobsId)), BudgetNotFoundException.CODE);
      assertThat(store.rows.get(bobsId).isDeleted()).isFalse();
    }

    @Test
    @DisplayName("xoá lần hai là no-op")
    void deleteTwiceIsNoOp() {
      BudgetView view = createFor(ledger.expense(ANN), "VND");
      store.releaseLocks();

      delete.delete(new DeleteBudgetCommand(ANN, view.id()));
      store.releaseLocks();
      BudgetView again = delete.delete(new DeleteBudgetCommand(ANN, view.id()));

      assertThat(again.deleted()).isTrue();
      assertThat(store.updates).isEqualTo(1);
    }
  }

  @Nested
  class Reconcile {

    @Test
    @DisplayName("danh mục bị xoá: xoá mọi ngân sách của nó, mọi tiền tệ (GS6)")
    void deletedCategoryRemovesAllBudgets() {
      UUID food = ledger.expense(ANN);
      UUID transport = ledger.expense(ANN);
      createFor(food, "VND");
      createFor(food, "USD");
      BudgetView other = createFor(transport, "VND");
      ledger.set(ANN, new CategoryRef(food, "EXPENSE", null, false, true));

      store.releaseLocks();
      int removed = reconcile.reconcile(new ReconcileBudgetsOfCategoryCommand(ANN, food));

      assertThat(removed).isEqualTo(2);
      assertThat(store.findActiveByCategory(ANN, food)).isEmpty();
      assertThat(store.rows.get(other.id()).isDeleted()).isFalse();
    }

    @Test
    @DisplayName("danh mục thành danh mục con: xoá ngân sách của nó")
    void reparentedCategoryRemovesBudgets() {
      UUID food = ledger.expense(ANN);
      UUID drinks = ledger.expense(ANN);
      createFor(drinks, "VND");
      ledger.set(ANN, new CategoryRef(drinks, "EXPENSE", food, false, false));

      store.releaseLocks();
      assertThat(reconcile.reconcile(new ReconcileBudgetsOfCategoryCommand(ANN, drinks)))
          .isEqualTo(1);
    }

    @Test
    @DisplayName("event tới 2 lần: lần sau không đổi gì")
    void reconcileIsIdempotent() {
      UUID food = ledger.expense(ANN);
      createFor(food, "VND");
      ledger.set(ANN, new CategoryRef(food, "EXPENSE", null, false, true));

      store.releaseLocks();
      reconcile.reconcile(new ReconcileBudgetsOfCategoryCommand(ANN, food));
      int updates = store.updates;
      store.releaseLocks();
      int second = reconcile.reconcile(new ReconcileBudgetsOfCategoryCommand(ANN, food));

      assertThat(second).isZero();
      assertThat(store.updates).isEqualTo(updates);
    }

    @Test
    @DisplayName("event cũ tới sau khi danh mục đã hợp lệ trở lại: không xoá nhầm")
    void staleEventDoesNotDeleteValidBudgets() {
      UUID food = ledger.expense(ANN);
      UUID archived = ledger.put(ANN, "EXPENSE", null, true, false);
      createFor(food, "VND");
      createFor(archived, "VND");

      store.releaseLocks();
      assertThat(reconcile.reconcile(new ReconcileBudgetsOfCategoryCommand(ANN, food))).isZero();
      store.releaseLocks();
      assertThat(reconcile.reconcile(new ReconcileBudgetsOfCategoryCommand(ANN, archived)))
          .isZero();
      assertThat(store.countActive(ANN)).isEqualTo(2);
    }

    @Test
    @DisplayName("danh mục không thấy: không xoá gì")
    void missingCategoryKeepsBudgets() {
      UUID food = ledger.expense(ANN);
      createFor(food, "VND");

      store.releaseLocks();
      int removed = reconcile.reconcile(new ReconcileBudgetsOfCategoryCommand(BOB, food));

      assertThat(removed).isZero();
      assertThat(store.countActive(ANN)).isEqualTo(1);
    }
  }

  @Test
  @DisplayName("change feed trả cả tombstone theo change_seq tăng dần, chỉ của chính user")
  void changeFeed() {
    UUID food = ledger.expense(ANN);
    BudgetView first = createFor(food, "VND");
    BudgetView second = createFor(food, "USD");
    store.releaseLocks();
    delete.delete(new DeleteBudgetCommand(ANN, first.id()));

    assertThat(feed.since(ANN, 0, 10))
        .extracting(change -> change.budget().id())
        .containsExactly(second.id(), first.id());
    assertThat(feed.find(ANN, first.id()))
        .map(BudgetChange::budget)
        .map(BudgetView::deleted)
        .contains(true);
    assertThat(feed.since(BOB, 0, 10)).isEmpty();
    assertThat(feed.find(BOB, first.id())).isEmpty();
  }
}
