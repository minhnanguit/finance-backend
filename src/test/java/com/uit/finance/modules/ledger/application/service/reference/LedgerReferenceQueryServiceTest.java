package com.uit.finance.modules.ledger.application.service.reference;

import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.ANN;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.BOB;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.NOW;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.category;
import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.modules.ledger.application.port.in.reference.CategoryRef;
import com.uit.finance.modules.ledger.application.service.support.LedgerFakes;
import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.modules.ledger.domain.model.category.CategoryDetails;
import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import com.uit.finance.modules.ledger.domain.model.category.CategoryKind;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LedgerReferenceQueryServiceTest {

  private LedgerFakes.Store store;
  private LedgerReferenceQueryService query;

  @BeforeEach
  void setUp() {
    store = new LedgerFakes.Store();
    query = new LedgerReferenceQueryService(store, store);
  }

  @Test
  @DisplayName("trả kind, cha, archived, deleted của danh mục")
  void exposesReferenceState() {
    Category food = category(ANN, CategoryKind.EXPENSE);
    Category coffee =
        Category.create(
            new CategoryId(UUID.randomUUID()),
            ANN,
            CategoryKind.EXPENSE,
            CategoryDetails.parse("Cà phê", food.getId().value(), null, null),
            id -> food);
    coffee.delete(NOW, () -> false);
    store.put(food);
    store.put(coffee);

    assertThat(query.findCategory(ANN, food.getId().value()))
        .contains(new CategoryRef(food.getId().value(), "EXPENSE", null, false, false));
    assertThat(query.findCategory(ANN, coffee.getId().value()))
        .hasValueSatisfying(
            ref -> {
              assertThat(ref.parentId()).isEqualTo(food.getId().value());
              assertThat(ref.deleted()).isTrue();
            });
  }

  @Test
  @DisplayName("danh mục của user khác trả rỗng, giống id chưa có (B2)")
  void foreignLooksMissing() {
    Category bobs = category(BOB, CategoryKind.EXPENSE);
    store.put(bobs);

    assertThat(query.findCategory(ANN, bobs.getId().value())).isEmpty();
    assertThat(query.findCategory(ANN, UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName("tra theo lô bỏ qua id không thấy và id của user khác")
  void batchSkipsMissingAndForeign() {
    Category food = category(ANN, CategoryKind.EXPENSE);
    Category bobs = category(BOB, CategoryKind.EXPENSE);
    store.put(food);
    store.put(bobs);

    assertThat(
            query.findCategories(
                ANN, Set.of(food.getId().value(), bobs.getId().value(), UUID.randomUUID())))
        .extracting(CategoryRef::id)
        .containsExactly(food.getId().value());
  }

  @Test
  @DisplayName("ví: trả tiền tệ, archived, deleted; ví của user khác trả rỗng (B2)")
  void exposesAccountState() {
    var mine = com.uit.finance.modules.ledger.domain.model.LedgerFixtures.account(ANN, "VND");
    var bobs = com.uit.finance.modules.ledger.domain.model.LedgerFixtures.account(BOB, "USD");
    store.put(mine);
    store.put(bobs);

    assertThat(query.findAccount(ANN, mine.getId().value()))
        .contains(
            new com.uit.finance.modules.ledger.application.port.in.reference.AccountRef(
                mine.getId().value(), "VND", false, false));
    assertThat(query.findAccount(ANN, bobs.getId().value())).isEmpty();
  }
}
