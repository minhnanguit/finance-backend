package com.mosaicglobal.finance.modules.identity.application.port.in;

public interface AuthenticateUserUseCase {

  AuthTokens authenticate(AuthenticateUserCommand command);

  record AuthenticateUserCommand(String email, String rawPassword, DeviceDescriptor device) {}
}
