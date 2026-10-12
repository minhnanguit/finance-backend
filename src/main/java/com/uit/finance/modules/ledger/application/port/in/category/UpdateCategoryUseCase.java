package com.uit.finance.modules.ledger.application.port.in.category;

import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;

/** Sửa danh mục. {@code kind} bất biến; cha tối đa 2 cấp (ADR-005 §2). */
public interface UpdateCategoryUseCase {

  CategoryView update(UpdateCategoryCommand command);

  record UpdateCategoryCommand(UserId userId, UUID categoryId, CategoryFields fields) {}
}
