package com.uit.finance.shared.security;

import org.springframework.security.authentication.AccountStatusException;

/**
 * Token hợp lệ nhưng tài khoản phía sau không còn dùng được (đã xoá, ADR-008). Trả {@code 403} kèm
 * {@link #code()} thay vì {@code 401}: refresh token không cứu được, app phải xoá dữ liệu trên máy
 * chứ không thử đăng nhập lại.
 *
 * <p>Là {@link AccountStatusException} nên {@code ProviderManager} ném ra ngay, không thử provider
 * khác, và {@link ProblemAuthenticationEntryPoint} nhận được đúng exception này.
 */
public final class InactiveAccountException extends AccountStatusException {

  private final String code;

  public InactiveAccountException(String code, String message) {
    super(message);
    this.code = code;
  }

  /** Mã ổn định cho client, ví dụ {@code identity.user_deleted}. */
  public String code() {
    return code;
  }
}
