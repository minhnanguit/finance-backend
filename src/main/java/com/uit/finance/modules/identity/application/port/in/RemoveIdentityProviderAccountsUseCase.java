package com.uit.finance.modules.identity.application.port.in;

/**
 * Xoá trên Keycloak tài khoản của mọi user đang/đã xoá mà chưa xoá bên IdP (ADR-008 A8). Chạy định
 * kỳ; Keycloak lỗi thì dừng, lần sau làm tiếp — không bao giờ bỏ cuộc.
 */
public interface RemoveIdentityProviderAccountsUseCase {

  /**
   * @return số tài khoản đã xoá xong bên IdP trong lần chạy này
   */
  int removePending();
}
