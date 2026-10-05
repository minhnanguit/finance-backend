package com.uit.finance.modules.ledger.application.port.out.category;

import com.uit.finance.modules.ledger.application.port.out.shared.Sequenced;
import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.Optional;

/** Đọc cho sync: bản ghi kèm {@code change_seq}, kể cả tombstone. */
public interface LoadCategoryChangesPort {

  /** Tối đa {@code limit} bản ghi có {@code change_seq > afterSeq}, tăng dần. */
  List<Sequenced<Category>> changesSince(UserId owner, long afterSeq, int limit);

  /** Id của user khác thì rỗng. */
  Optional<Sequenced<Category>> findSequenced(UserId owner, CategoryId id);
}
