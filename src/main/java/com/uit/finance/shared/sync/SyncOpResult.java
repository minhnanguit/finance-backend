package com.uit.finance.shared.sync;

import com.uit.finance.shared.kernel.Ensure;
import org.jspecify.annotations.Nullable;

/**
 * Handler quyết định gì với một op. Bản ghi hiện tại không nằm đây: {@code sync} tự đọc lại sau khi
 * transaction của op kết thúc, để không trả về dữ liệu của một lần ghi đã bị rollback.
 */
public record SyncOpResult(SyncOutcome outcome, @Nullable String code) {

  public SyncOpResult {
    Ensure.notNull(outcome, "outcome");
  }

  public static SyncOpResult applied() {
    return new SyncOpResult(SyncOutcome.APPLIED, null);
  }

  public static SyncOpResult rejected(String code) {
    return new SyncOpResult(SyncOutcome.REJECTED, code);
  }

  public static SyncOpResult conflict(String code) {
    return new SyncOpResult(SyncOutcome.CONFLICT, code);
  }

  public static SyncOpResult retry(String code) {
    return new SyncOpResult(SyncOutcome.RETRY, code);
  }
}
