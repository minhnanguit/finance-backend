package com.uit.finance.modules.ledger.domain.model.shared;

import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;

/** Ví hoặc danh mục mà một bản ghi khác trỏ tới. */
public interface Referenceable {

  LedgerEntity entity();

  UUID uuid();

  boolean isOwnedBy(UserId owner);

  boolean isDeleted();

  boolean isArchived();
}
