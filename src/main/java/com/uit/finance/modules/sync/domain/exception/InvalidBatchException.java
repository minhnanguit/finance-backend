package com.uit.finance.modules.sync.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;

/** Cả batch sai hình dạng (quá nhiều op, thiếu deviceId...). Khác lỗi của từng op. */
public class InvalidBatchException extends SyncException {

  public static final String CODE = "sync.invalid_batch";

  public InvalidBatchException(String reason) {
    super(ErrorCategory.VALIDATION, CODE, "Invalid sync batch: " + reason);
  }
}
