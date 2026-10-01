package com.uit.finance.modules.ledger.application.port.in;

import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;

/** Ẩn hoặc hiện lại danh mục. Danh mục archive không nhận giao dịch mới. */
public interface ArchiveCategoryUseCase {

  CategoryView setArchived(ArchiveCategoryCommand command);

  record ArchiveCategoryCommand(UserId userId, UUID categoryId, boolean archived) {}
}
