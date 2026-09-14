package com.mosaicglobal.finance.modules.identity.adapter.in.web;

import com.mosaicglobal.finance.api.v1.AuthApi;
import com.mosaicglobal.finance.api.v1.model.LoginRequest;
import com.mosaicglobal.finance.api.v1.model.LogoutRequest;
import com.mosaicglobal.finance.api.v1.model.RefreshRequest;
import com.mosaicglobal.finance.api.v1.model.RegisterRequest;
import com.mosaicglobal.finance.api.v1.model.TokenPair;
import com.mosaicglobal.finance.modules.identity.application.port.in.AuthenticateUserUseCase;
import com.mosaicglobal.finance.modules.identity.application.port.in.RefreshSessionUseCase;
import com.mosaicglobal.finance.modules.identity.application.port.in.RegisterUserUseCase;
import com.mosaicglobal.finance.modules.identity.application.port.in.RevokeSessionUseCase;
import com.mosaicglobal.finance.modules.identity.application.port.in.RevokeSessionUseCase.RevokeSessionCommand;
import com.mosaicglobal.finance.shared.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements the generated {@link AuthApi} contract. Thin: maps DTO ↔ command and delegates. The
 * {@code Idempotency-Key} parameter is consumed by the shared filter; it is part of the signature
 * only because the contract documents it.
 */
@RestController
class AuthController implements AuthApi {

  private final RegisterUserUseCase registerUser;
  private final AuthenticateUserUseCase authenticateUser;
  private final RefreshSessionUseCase refreshSession;
  private final RevokeSessionUseCase revokeSession;
  private final AuthWebMapper mapper;

  AuthController(
      RegisterUserUseCase registerUser,
      AuthenticateUserUseCase authenticateUser,
      RefreshSessionUseCase refreshSession,
      RevokeSessionUseCase revokeSession,
      AuthWebMapper mapper) {
    this.registerUser = registerUser;
    this.authenticateUser = authenticateUser;
    this.refreshSession = refreshSession;
    this.revokeSession = revokeSession;
    this.mapper = mapper;
  }

  @Override
  public ResponseEntity<TokenPair> register(UUID idempotencyKey, RegisterRequest request) {
    TokenPair tokens = mapper.toTokenPair(registerUser.register(mapper.toCommand(request)));
    return ResponseEntity.status(HttpStatus.CREATED).body(tokens);
  }

  @Override
  public ResponseEntity<TokenPair> login(UUID idempotencyKey, LoginRequest request) {
    return ResponseEntity.ok(
        mapper.toTokenPair(authenticateUser.authenticate(mapper.toCommand(request))));
  }

  @Override
  public ResponseEntity<TokenPair> refresh(UUID idempotencyKey, RefreshRequest request) {
    return ResponseEntity.ok(mapper.toTokenPair(refreshSession.refresh(mapper.toCommand(request))));
  }

  @Override
  public ResponseEntity<Void> logout(UUID idempotencyKey, LogoutRequest request) {
    revokeSession.revoke(
        new RevokeSessionCommand(AuthenticatedUser.requireCurrent(), request.getRefreshToken()));
    return ResponseEntity.noContent().build();
  }
}
