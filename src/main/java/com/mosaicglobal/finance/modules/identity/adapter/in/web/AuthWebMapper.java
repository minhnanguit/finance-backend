package com.mosaicglobal.finance.modules.identity.adapter.in.web;

import com.mosaicglobal.finance.api.v1.model.DeviceInfo;
import com.mosaicglobal.finance.api.v1.model.LoginRequest;
import com.mosaicglobal.finance.api.v1.model.RefreshRequest;
import com.mosaicglobal.finance.api.v1.model.RegisterRequest;
import com.mosaicglobal.finance.api.v1.model.TokenPair;
import com.mosaicglobal.finance.modules.identity.application.port.in.AuthTokens;
import com.mosaicglobal.finance.modules.identity.application.port.in.AuthenticateUserUseCase.AuthenticateUserCommand;
import com.mosaicglobal.finance.modules.identity.application.port.in.DeviceDescriptor;
import com.mosaicglobal.finance.modules.identity.application.port.in.GetUserProfileUseCase.UserProfile;
import com.mosaicglobal.finance.modules.identity.application.port.in.RefreshSessionUseCase.RefreshSessionCommand;
import com.mosaicglobal.finance.modules.identity.application.port.in.RegisterUserUseCase.RegisterUserCommand;
import com.mosaicglobal.finance.modules.identity.domain.model.DevicePlatform;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/** DTO ↔ application command mapping. Explicit on purpose: the contract is the boundary. */
@Component
class AuthWebMapper {

  private static final String BEARER = "Bearer";

  RegisterUserCommand toCommand(RegisterRequest request) {
    return new RegisterUserCommand(
        request.getEmail(),
        request.getPassword(),
        request.getDisplayName(),
        toDescriptor(request.getDevice()));
  }

  AuthenticateUserCommand toCommand(LoginRequest request) {
    return new AuthenticateUserCommand(
        request.getEmail(), request.getPassword(), toDescriptor(request.getDevice()));
  }

  RefreshSessionCommand toCommand(RefreshRequest request) {
    return new RefreshSessionCommand(request.getRefreshToken(), request.getDeviceId());
  }

  TokenPair toTokenPair(AuthTokens tokens) {
    return new TokenPair()
        .accessToken(tokens.accessToken())
        .refreshToken(tokens.refreshToken())
        .tokenType(BEARER)
        .expiresIn((int) tokens.accessTokenTtl().toSeconds());
  }

  com.mosaicglobal.finance.api.v1.model.UserProfile toProfile(UserProfile profile) {
    return new com.mosaicglobal.finance.api.v1.model.UserProfile()
        .id(profile.id().value())
        .email(profile.email())
        .displayName(profile.displayName())
        .createdAt(profile.createdAt().atOffset(ZoneOffset.UTC));
  }

  private static DeviceDescriptor toDescriptor(DeviceInfo device) {
    return new DeviceDescriptor(
        device.getDeviceId(),
        device.getDeviceName(),
        DevicePlatform.valueOf(device.getPlatform().name()));
  }
}
