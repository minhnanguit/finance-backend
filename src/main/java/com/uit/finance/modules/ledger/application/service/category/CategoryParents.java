package com.uit.finance.modules.ledger.application.service.category;

import com.uit.finance.modules.ledger.application.port.out.category.LoadCategoryPort;
import com.uit.finance.modules.ledger.domain.exception.ReferencePendingException;
import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.shared.kernel.UserId;
import java.util.function.Function;

/** Load danh mục cha cho {@link Category}, chỉ được gọi khi cha thật sự đổi. */
final class CategoryParents {

  private CategoryParents() {}

  /**
   * Không thấy cha của chính user: có thể chưa sync tới, có thể là id của user khác. Hai trường hợp
   * cùng trả {@code ledger.reference_pending} (ADR-006 B2).
   */
  static Function<CategoryId, Category> of(LoadCategoryPort loadCategory, UserId owner) {
    return parentId ->
        loadCategory
            .find(owner, parentId)
            .orElseThrow(
                () -> new ReferencePendingException(LedgerEntity.CATEGORY, parentId.value()));
  }
}
