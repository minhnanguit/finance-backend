package com.uit.finance.modules.ledger.domain.model.category;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultCategoryTemplateTest {

  @Test
  @DisplayName("16 mẫu, key không trùng, đúng danh sách ADR-005 §6")
  void matchesAdr() {
    assertThat(Arrays.stream(DefaultCategoryTemplate.values()).map(t -> t.key().value()))
        .doesNotHaveDuplicates()
        .containsExactlyInAnyOrder(
            "food",
            "transport",
            "shopping",
            "bills",
            "housing",
            "health",
            "education",
            "entertainment",
            "family",
            "fee",
            "other_expense",
            "salary",
            "bonus",
            "investment",
            "gift",
            "other_income");
  }

  @Test
  @DisplayName("có mẫu phí giao dịch là khoản chi (D3)")
  void feeIsAnExpense() {
    assertThat(DefaultCategoryTemplate.FEE.kind()).isEqualTo(CategoryKind.EXPENSE);
  }

  @Test
  @DisplayName("mẫu nào cũng là danh mục gốc, có icon và màu")
  void templatesAreRootsWithIconAndColor() {
    assertThat(DefaultCategoryTemplate.values())
        .allSatisfy(
            template -> {
              assertThat(template.details().parentId()).isNull();
              assertThat(template.details().icon()).isNotNull();
              assertThat(template.details().color()).isNotNull();
            });
  }
}
