package com.uit.finance.modules.sync.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;

public class InvalidCursorException extends SyncException {

  public static final String CODE = "sync.invalid_cursor";

  public InvalidCursorException() {
    super(ErrorCategory.VALIDATION, CODE, "Cursor is malformed; send back the nextCursor you got");
  }
}
