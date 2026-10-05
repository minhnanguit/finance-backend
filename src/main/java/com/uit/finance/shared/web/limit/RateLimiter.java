package com.uit.finance.shared.web.limit;

import java.time.Duration;

/** Bộ đếm fixed window. Tách interface để filter test được không cần Redis. */
public interface RateLimiter {

  /** Tính thêm một request vào {@code bucket} và cho biết có được đi tiếp không. */
  Decision tryAcquire(String bucket, int limit, Duration window);

  /**
   * @param retryAfterSeconds số giây tới khi cửa sổ hiện tại kết thúc; chỉ có nghĩa khi bị chặn
   */
  record Decision(boolean allowed, long retryAfterSeconds) {

    static Decision allow() {
      return new Decision(true, 0);
    }
  }
}
