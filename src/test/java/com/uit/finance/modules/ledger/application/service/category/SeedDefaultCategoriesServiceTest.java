package com.uit.finance.modules.ledger.application.service.category;

import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.ANN;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.BOB;
import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.modules.ledger.application.service.support.LedgerFakes;
import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.modules.ledger.domain.model.category.CategoryDetails;
import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import com.uit.finance.modules.ledger.domain.model.category.CategoryKind;
import com.uit.finance.modules.ledger.domain.model.category.DefaultCategoryTemplate;
import com.uit.finance.modules.ledger.domain.model.category.TemplateKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeedDefaultCategoriesServiceTest {

  private final LedgerFakes.Store store = new LedgerFakes.Store();
  private final SeedDefaultCategoriesService service = new SeedDefaultCategoriesService(store);

  @Test
  @DisplayName("lần đầu tạo đủ 16 danh mục, lần sau không tạo trùng")
  void seedIsIdempotent() {
    assertThat(service.seed(ANN)).isEqualTo(DefaultCategoryTemplate.values().length);
    assertThat(service.seed(ANN)).isZero();
    assertThat(store.countCategories(ANN)).isEqualTo(16);
  }

  @Test
  @DisplayName("danh mục user đã sửa được giữ nguyên khi seed lại")
  void userEditsSurviveReseed() {
    service.seed(ANN);
    CategoryId foodId = CategoryId.forTemplate(ANN, new TemplateKey("food"));
    Category food = store.find(ANN, foodId).orElseThrow();
    food.revise(
        CategoryKind.EXPENSE,
        CategoryDetails.parse("Ăn sáng", null, null, null),
        id -> {
          throw new AssertionError();
        },
        () -> false);
    store.update(ANN, food);

    service.seed(ANN);

    assertThat(store.find(ANN, foodId).orElseThrow().getDetails().name().value())
        .isEqualTo("Ăn sáng");
  }

  @Test
  @DisplayName("mỗi user có bộ riêng, id không đụng nhau")
  void eachUserGetsOwnCopy() {
    service.seed(ANN);

    assertThat(service.seed(BOB)).isEqualTo(16);
    assertThat(store.find(BOB, CategoryId.forTemplate(ANN, new TemplateKey("fee")))).isEmpty();
  }
}
