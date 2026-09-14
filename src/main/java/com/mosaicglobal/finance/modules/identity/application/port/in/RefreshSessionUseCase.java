package com.mosaicglobal.finance.modules.identity.application.port.in;

public interface RefreshSessionUseCase {

  AuthTokens refresh(RefreshSessionCommand command);

  record RefreshSessionCommand(String refreshToken, String deviceId) {}
}
