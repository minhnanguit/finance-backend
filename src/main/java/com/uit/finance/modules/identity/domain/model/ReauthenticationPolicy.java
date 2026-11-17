package com.uit.finance.modules.identity.domain.model;

import com.uit.finance.modules.identity.domain.exception.ReauthenticationRequiredException;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Thao tác không hoàn tác được (xoá tài khoản) đòi user vừa nhập lại mật khẩu/passkey: lần đăng
 * nhập gần nhất ({@code auth_time}) phải trong {@link #MAX_AGE} (ADR-008 A2, ASVS V2.2). Refresh
 * token không làm mốc này mới lên, nên điện thoại đang mở khoá trong tay người khác không đủ để
 * xoá.
 */
public final class ReauthenticationPolicy {

  public static final Duration MAX_AGE = Duration.ofMinutes(5);

  /** Đồng hồ Keycloak và server lệch nhau một chút vẫn chấp nhận. */
  public static final Duration CLOCK_SKEW = Duration.ofMinutes(1);

  private ReauthenticationPolicy() {}

  /**
   * @throws ReauthenticationRequiredException khi không có {@code auth_time}, quá cũ, hoặc ở tương
   *     lai xa hơn độ lệch cho phép
   */
  public static void requireRecent(@Nullable Instant authenticatedAt, Instant now) {
    if (authenticatedAt == null
        || authenticatedAt.isBefore(now.minus(MAX_AGE))
        || authenticatedAt.isAfter(now.plus(CLOCK_SKEW))) {
      throw new ReauthenticationRequiredException();
    }
  }
}
