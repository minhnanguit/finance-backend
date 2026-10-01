package com.uit.finance.modules.ledger.application.port.in;

import com.uit.finance.shared.kernel.UserId;

/**
 * Tạo bộ danh mục mặc định cho user (ADR-005 §6). Gọi bao nhiêu lần cũng được: id là UUIDv5 nên bản
 * đã có được giữ nguyên, kể cả khi user đã sửa, archive hay xoá nó.
 */
public interface SeedDefaultCategoriesUseCase {

  /**
   * @return số danh mục vừa được tạo ở lần gọi này
   */
  int seed(UserId userId);
}
