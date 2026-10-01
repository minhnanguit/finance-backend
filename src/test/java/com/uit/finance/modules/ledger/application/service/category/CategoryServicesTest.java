package com.uit.finance.modules.ledger.application.service.category;

import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.ANN;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.BOB;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.NOW;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.assertRejected;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.category;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.uit.finance.modules.ledger.application.port.in.category.CategoryFields;
import com.uit.finance.modules.ledger.application.port.in.category.CategoryView;
import com.uit.finance.modules.ledger.application.port.in.category.CreateCategoryUseCase.CreateCategoryCommand;
import com.uit.finance.modules.ledger.application.port.in.category.DeleteCategoryUseCase.DeleteCategoryCommand;
import com.uit.finance.modules.ledger.application.port.in.category.UpdateCategoryUseCase.UpdateCategoryCommand;
import com.uit.finance.modules.ledger.application.service.support.LedgerFakes;
import com.uit.finance.modules.ledger.domain.exception.InUseException;
import com.uit.finance.modules.ledger.domain.exception.KindImmutableException;
import com.uit.finance.modules.ledger.domain.exception.LimitExceededException;
import com.uit.finance.modules.ledger.domain.exception.ReferencePendingException;
import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.modules.ledger.domain.model.category.CategoryKind;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerLimits;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CategoryServicesTest {

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private LedgerFakes.Store store;
  private CreateCategoryService create;
  private UpdateCategoryService update;
  private DeleteCategoryService delete;

  @BeforeEach
  void setUp() {
    store = new LedgerFakes.Store();
    create = new CreateCategoryService(store, store, store);
    update = new UpdateCategoryService(store, store, store);
    delete = new DeleteCategoryService(store, store, store, clock);
  }

  private static CategoryFields expense(String name, UUID parentId) {
    return new CategoryFields("EXPENSE", name, parentId, "local_cafe", "#795548");
  }

  @Test
  @DisplayName("tạo danh mục con dưới danh mục gốc của mình")
  void createsChild() {
    Category food = category(ANN, CategoryKind.EXPENSE);
    store.put(food);

    CategoryView coffee =
        create.create(
            new CreateCategoryCommand(
                ANN, UUID.randomUUID(), expense("Cà phê", food.getId().value())));

    assertThat(coffee.parentId()).isEqualTo(food.getId().value());
    assertThat(coffee.icon()).isEqualTo("local_cafe");
  }

  @Test
  @DisplayName("cha chưa tới và cha của user khác trả y hệt nhau (B2)")
  void missingAndForeignParentsLookIdentical() {
    Category bobsFood = category(BOB, CategoryKind.EXPENSE);
    UUID parentId = bobsFood.getId().value();
    UUID childId = UUID.randomUUID();

    Throwable missing =
        catchThrowable(
            () -> create.create(new CreateCategoryCommand(ANN, childId, expense("Con", parentId))));
    store.put(bobsFood);
    Throwable foreign =
        catchThrowable(
            () -> create.create(new CreateCategoryCommand(ANN, childId, expense("Con", parentId))));

    assertThat(missing).isInstanceOf(ReferencePendingException.class);
    assertThat(foreign)
        .isInstanceOf(ReferencePendingException.class)
        .hasMessage(missing.getMessage());
  }

  @Test
  @DisplayName("tối đa 300 danh mục mỗi user")
  void categoryLimit() {
    for (int i = 0; i < LedgerLimits.MAX_CATEGORIES_PER_USER; i++) {
      store.put(category(ANN, CategoryKind.EXPENSE));
    }

    assertRejected(
        () ->
            create.create(
                new CreateCategoryCommand(ANN, UUID.randomUUID(), expense("Thứ 301", null))),
        LimitExceededException.CODE);
  }

  @Test
  @DisplayName("đổi loại danh mục: kind_immutable, không ghi gì")
  void kindCannotChange() {
    Category food = category(ANN, CategoryKind.EXPENSE);
    store.put(food);

    assertRejected(
        () ->
            update.update(
                new UpdateCategoryCommand(
                    ANN,
                    food.getId().value(),
                    new CategoryFields("INCOME", "Ăn uống", null, null, null))),
        KindImmutableException.CODE);
    assertThat(store.updates).isZero();
  }

  @Test
  @DisplayName("danh mục còn con thì không xoá được")
  void cannotDeleteParentWithChildren() {
    Category food = category(ANN, CategoryKind.EXPENSE);
    store.put(food);
    create.create(
        new CreateCategoryCommand(ANN, UUID.randomUUID(), expense("Cà phê", food.getId().value())));

    assertRejected(
        () -> delete.delete(new DeleteCategoryCommand(ANN, food.getId().value())),
        InUseException.CODE);
  }
}
