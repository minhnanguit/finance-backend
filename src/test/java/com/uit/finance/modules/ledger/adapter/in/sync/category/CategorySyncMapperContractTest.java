package com.uit.finance.modules.ledger.adapter.in.sync.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.api.v1.model.CategoryData;
import com.uit.finance.modules.ledger.adapter.in.sync.support.ContractFields;
import com.uit.finance.modules.ledger.application.port.in.category.CategoryChangeFeedUseCase.CategoryChange;
import com.uit.finance.modules.ledger.application.port.in.category.CategoryView;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** {@code templateKey} là readOnly: có ở chiều trả ra, không có ở chiều nhận vào. */
class CategorySyncMapperContractTest {

  @Test
  @DisplayName("field nhận vào = CategoryData trừ templateKey; field trả ra = CategoryData")
  void matchesContract() {
    Set<String> contract = ContractFields.of(CategoryData.class);
    Set<String> writable = new HashSet<>(contract);
    writable.remove("templateKey");
    CategoryView view =
        new CategoryView(
            UUID.randomUUID(), "EXPENSE", "Ăn uống", null, null, null, "food", null, false);

    assertThat(CategorySyncMapper.INPUT_FIELDS).isEqualTo(writable);
    assertThat(CategorySyncMapper.toChange(new CategoryChange(view, 1)).data().keySet())
        .isEqualTo(contract);
  }
}
