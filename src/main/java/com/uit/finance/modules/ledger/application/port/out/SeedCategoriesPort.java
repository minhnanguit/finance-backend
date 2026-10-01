package com.uit.finance.modules.ledger.application.port.out;

import com.uit.finance.modules.ledger.domain.model.Category;
import com.uit.finance.shared.kernel.UserId;
import java.util.List;

/** Tách khỏi {@link SaveCategoryPort} để adapter ghi cả bộ mặc định trong một câu lệnh. */
public interface SeedCategoriesPort {

  /**
   * {@code INSERT ... ON CONFLICT (id) DO NOTHING} cho cả danh sách. Bản đã có giữ nguyên, kể cả
   * tombstone.
   *
   * @return số dòng thật sự được insert
   */
  int insertAllIfAbsent(UserId owner, List<Category> categories);
}
