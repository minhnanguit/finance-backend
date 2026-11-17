package com.uit.finance.modules.sync.domain.exception;

import com.uit.finance.shared.kernel.DomainException;
import com.uit.finance.shared.kernel.ErrorCategory;

/** Gốc lỗi của module sync, mã dạng {@code sync.<reason>}. */
public abstract class SyncException extends DomainException {

  protected SyncException(ErrorCategory category, String code, String message) {
    super(category, code, message);
  }
}
