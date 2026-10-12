package com.uit.finance.modules.ledger.application.port.out.category;

import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import com.uit.finance.shared.kernel.UserId;
import java.util.Optional;

public interface LoadCategoryPort {

  /** Trả cả bản đã xoá (tombstone). Id của user khác thì trả rỗng. */
  Optional<Category> find(UserId owner, CategoryId id);
}
