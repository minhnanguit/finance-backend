package com.uit.finance.modules.sync.application.port.out;

import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;

/** Bảng {@code user_sync_state}: số cuối đã phát và trạng thái khởi tạo của user. */
public interface SyncStatePort {

  boolean isInitialized(UserId owner);

  /**
   * Khoá dòng của user tới hết transaction (tạo dòng nếu chưa có). Mọi lần ghi của cùng một user
   * xếp hàng ở đây, nên luật kiểu "đếm rồi ghi" (≤ 50 ví, ví còn giao dịch thì không xoá...) không
   * bị một request song song lách qua. User khác nhau không chờ nhau (ADR-002 §7).
   */
  void lockWrites(UserId owner);

  /**
   * Khoá dòng của user tới hết transaction (tạo dòng nếu chưa có).
   *
   * @return {@code true} nếu user vẫn chưa được khởi tạo, đọc sau khi đã giữ khoá
   */
  boolean lockForInitialization(UserId owner);

  void markInitialized(UserId owner, Instant at);

  /** Số {@code change_seq} cuối đã phát cho user; 0 nếu chưa ghi gì. */
  long lastIssuedSeq(UserId owner);
}
