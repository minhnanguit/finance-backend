package com.mosaicglobal.finance.modules.identity.application.port.in;

public interface RegisterUserUseCase {

  AuthTokens register(RegisterUserCommand command);

  record RegisterUserCommand(
      String email, String rawPassword, String displayName, DeviceDescriptor device) {}
}
