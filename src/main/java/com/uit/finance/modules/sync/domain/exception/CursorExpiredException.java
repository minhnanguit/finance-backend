package com.uit.finance.modules.sync.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;

/**
 * Server đã dọn tombstone cũ hơn cursor này (máy offline quá lâu, ADR-008 T3): không còn báo được
 * hết những gì đã bị xoá. Client gửi hết thay đổi đang chờ, xoá dữ liệu đã đồng bộ trên máy rồi
 * pull lại từ đầu.
 */
public class CursorExpiredException extends SyncException {

  public static final String CODE = "sync.cursor_expired";

  public CursorExpiredException() {
    super(
        ErrorCategory.GONE,
        CODE,
        "Deleted records older than this cursor were purged; reset local data and pull from start");
  }
}
