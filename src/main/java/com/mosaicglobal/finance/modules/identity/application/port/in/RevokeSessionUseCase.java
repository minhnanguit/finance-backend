package com.mosaicglobal.finance.modules.identity.application.port.in;

import com.mosaicglobal.finance.shared.kernel.UserId;

public interface RevokeSessionUseCase {

  /** Idempotent: an unknown or foreign token is silently ignored. */
  void revoke(RevokeSessionCommand command);

  record RevokeSessionCommand(UserId requester, String refreshToken) {}
}
