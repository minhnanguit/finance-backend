package com.uit.finance.modules.ledger.application.port.in.category;

import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;

/**
 * Xoá danh mục (tombstone). Còn giao dịch hoặc danh mục con: {@code ledger.in_use}, chỉ archive
 * được (ADR-005 D7).
 */
public interface DeleteCategoryUseCase {

  CategoryView delete(DeleteCategoryCommand command);

  record DeleteCategoryCommand(UserId userId, UUID categoryId) {}
}
