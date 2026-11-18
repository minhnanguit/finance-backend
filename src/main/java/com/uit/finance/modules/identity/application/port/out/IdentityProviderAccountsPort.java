package com.uit.finance.modules.identity.application.port.out;

import com.uit.finance.modules.identity.domain.model.ExternalSubject;

/** Tài khoản bên Keycloak (Admin REST API). */
public interface IdentityProviderAccountsPort {

  /**
   * {@code false} khi client bị tắt bằng cấu hình (chạy local không có Keycloak). Lúc đó không gọi
   * {@link #deleteAccount}: việc xoá còn nợ được giữ lại, bật lên thì làm bù.
   */
  boolean enabled();

  /**
   * Xoá tài khoản có {@code sub} này. Không còn tài khoản đó thì coi như xong. Keycloak không trả
   * lời được (hoặc cấu hình sai) thì ném exception; lần chạy sau thử lại.
   */
  void deleteAccount(ExternalSubject subject);
}
