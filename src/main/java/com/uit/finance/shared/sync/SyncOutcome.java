package com.uit.finance.shared.sync;

/** Kết quả của một op (ADR-002 §4). */
public enum SyncOutcome {
  /** Đã ghi. */
  APPLIED,
  /** {@code opId} đã xử lý xong trước đó; không ghi lại. */
  DUPLICATE,
  /** Server giữ bản của mình, ví dụ bản ghi đã bị xoá (xoá luôn thắng). */
  CONFLICT,
  /** Vi phạm luật hoặc không phải của user; client bỏ op. */
  REJECTED,
  /** Thứ được trỏ tới chưa tới; client giữ op và thử lại sau. */
  RETRY;

  /** Mọi kết quả trừ {@code RETRY} là chốt: gửi lại cùng {@code opId} sẽ nhận {@code DUPLICATE}. */
  public boolean isFinal() {
    return this != RETRY;
  }
}
