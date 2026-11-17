package com.uit.finance.shared.web.limit;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Fixed window trên Redis (ADR-002 §8): mỗi cửa sổ một key, {@code INCR} rồi đặt hạn cho key.
 *
 * <p>Hai lệnh chạy trong một script Lua nên nguyên tử: không bao giờ có key thiếu hạn sống mãi nếu
 * process chết giữa chừng. Mọi instance dùng chung Redis nên giới hạn đúng khi scale ngang.
 *
 * <p>Redis chết thì **cho qua** (fail-open, ADR-006 B4): rate limit bảo vệ tài nguyên, không phải
 * kiểm soát truy cập, và không được làm sập sync.
 */
public class RedisRateLimiter implements RateLimiter {

  private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
  private static final String KEY_PREFIX = "rl:v1:";

  private static final RedisScript<Long> INCREMENT =
      RedisScript.of(
          """
          local count = redis.call('INCR', KEYS[1])
          if count == 1 then
            redis.call('PEXPIRE', KEYS[1], ARGV[1])
          end
          return count
          """,
          Long.class);

  private final StringRedisTemplate redis;
  private final Clock clock;

  public RedisRateLimiter(StringRedisTemplate redis, Clock clock) {
    this.redis = redis;
    this.clock = clock;
  }

  @Override
  public Decision tryAcquire(String bucket, int limit, Duration window) {
    long windowMillis = window.toMillis();
    long now = clock.millis();
    long windowIndex = now / windowMillis;
    long millisLeft = (windowIndex + 1) * windowMillis - now;

    Long count;
    try {
      count =
          redis.execute(
              INCREMENT,
              List.of(KEY_PREFIX + bucket + ':' + windowIndex),
              Long.toString(windowMillis));
    } catch (DataAccessException e) {
      log.warn(
          "Rate limiter unavailable ({}); letting the request through", e.getClass().getName());
      return Decision.allow();
    }
    if (count == null || count <= limit) {
      return Decision.allow();
    }
    return new Decision(false, Math.max(1, (millisLeft + 999) / 1000));
  }
}
