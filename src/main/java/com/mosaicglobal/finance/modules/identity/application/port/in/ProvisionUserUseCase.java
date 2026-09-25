package com.mosaicglobal.finance.modules.identity.application.port.in;

import com.mosaicglobal.finance.shared.kernel.UserId;

/**
 * Bảo đảm có local account cho một {@code sub} Keycloak và trả internal id. Chạy trên
 * authentication path mỗi khi cache miss, nên phải nhẹ và an toàn khi gọi concurrent.
 */
public interface ProvisionUserUseCase {

  UserId provision(ProvisionUserCommand command);

  /**
   * {@code email} và {@code displayName} là giá trị hiện tại trên Keycloak, có thể đổi giữa các lần
   * gọi.
   */
  record ProvisionUserCommand(String externalSubject, String email, String displayName) {}
}
