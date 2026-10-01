package com.uit.finance.modules.ledger.application.port.in.category;

import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;

/**
 * Tạo danh mục theo id do client sinh, cùng quy tắc gửi lại / id của người khác / đã xoá như {@link
 * CreateAccountUseCase}. Danh mục cha chưa thấy (chưa sync tới hoặc của user khác): {@code
 * ledger.reference_pending}.
 */
public interface CreateCategoryUseCase {

  CategoryView create(CreateCategoryCommand command);

  record CreateCategoryCommand(UserId userId, UUID categoryId, CategoryFields fields) {}
}
