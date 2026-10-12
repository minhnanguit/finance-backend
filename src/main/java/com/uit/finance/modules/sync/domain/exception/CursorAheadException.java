package com.uit.finance.modules.sync.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;

/**
 * Cursor lớn hơn số cuối server đã phát, ví dụ sau khi khôi phục DB. Client xoá cursor rồi pull lại
 * từ đầu; áp lại là idempotent nên an toàn (ADR-002 §2).
 */
public class CursorAheadException extends SyncException {

  public static final String CODE = "sync.cursor_ahead";

  public CursorAheadException() {
    super(ErrorCategory.CONFLICT, CODE, "Cursor is ahead of the server; pull again from the start");
  }
}
