package com.uit.finance.modules.ledger.application.port.in.reference;

import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Đọc trạng thái danh mục cho module khác (ADR-007 §7). Trả cả bản đã xoá ({@code deleted = true}).
 * Id chưa có hoặc của user khác thì rỗng: hai trường hợp không phân biệt được (ADR-006 B2).
 */
public interface LedgerReferenceQuery {

  Optional<CategoryRef> findCategory(UserId owner, UUID categoryId);

  /** Một query cho cả lô. Id không thấy bị bỏ qua, thứ tự không bảo đảm. */
  List<CategoryRef> findCategories(UserId owner, Set<UUID> categoryIds);
}
