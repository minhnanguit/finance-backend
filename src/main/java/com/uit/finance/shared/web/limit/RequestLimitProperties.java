package com.uit.finance.shared.web.limit;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Giới hạn theo endpoint (ADR-006 B4). Thêm giới hạn cho endpoint mới = thêm một dòng cấu hình,
 * không sửa code.
 */
@ConfigurationProperties(prefix = "app.request-limits")
public record RequestLimitProperties(
    /** Tắt rate limit (ví dụ trong test tải). Giới hạn body luôn bật. */
    @DefaultValue("true") boolean rateLimitEnabled,
    @DefaultValue List<RateLimitRule> rateLimits,
    @DefaultValue List<BodyLimitRule> bodyLimits) {

  public RequestLimitProperties {
    rateLimits = List.copyOf(rateLimits);
    bodyLimits = List.copyOf(bodyLimits);
  }

  /**
   * Tối đa {@code limit} request mỗi {@code window} cho mỗi user, trên đúng {@code method} + {@code
   * path}.
   *
   * @param name tên bucket trong Redis, đổi tên là reset bộ đếm
   */
  public record RateLimitRule(String name, String method, String path, int limit, Duration window) {

    boolean matches(String requestMethod, String requestPath) {
      return method.equalsIgnoreCase(requestMethod) && path.equals(requestPath);
    }
  }

  public record BodyLimitRule(String method, String path, DataSize maxSize) {

    boolean matches(String requestMethod, String requestPath) {
      return method.equalsIgnoreCase(requestMethod) && path.equals(requestPath);
    }
  }
}
