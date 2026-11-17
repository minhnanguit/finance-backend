package com.uit.finance.modules.ledger.application.port.out.category;

import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import com.uit.finance.shared.kernel.UserId;

public interface CategoryUsagePort {

  /** Số danh mục chưa xoá của user, kể cả đã archive (giới hạn ADR-006 B4). */
  long countCategories(UserId owner);

  /** Có giao dịch chưa xoá nào trỏ vào danh mục, kể cả {@code DRAFT}. */
  boolean isReferenced(UserId owner, CategoryId id);

  /** Có danh mục con chưa xoá. */
  boolean hasActiveChildren(UserId owner, CategoryId id);
}
