package com.uit.finance.modules.identity.adapter.in.scheduler;

import com.uit.finance.modules.identity.application.port.in.RemoveIdentityProviderAccountsUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mỗi {@code idp-removal-interval} (mặc định 15 giây) xoá trên Keycloak các tài khoản user vừa yêu
 * cầu xoá (ADR-008 A8). Chậm nhất chừng đó sau khi bấm xoá, mật khẩu và refresh token trên mọi máy
 * hết dùng được; Keycloak lỗi thì lần sau thử lại.
 */
@Component
class IdentityProviderRemovalJob {

  private final RemoveIdentityProviderAccountsUseCase removeAccounts;

  IdentityProviderRemovalJob(RemoveIdentityProviderAccountsUseCase removeAccounts) {
    this.removeAccounts = removeAccounts;
  }

  @Scheduled(
      fixedDelayString = "${app.identity.deletion.idp-removal-interval:15s}",
      initialDelayString = "${app.identity.deletion.idp-removal-interval:15s}")
  void removePendingAccounts() {
    removeAccounts.removePending();
  }
}
