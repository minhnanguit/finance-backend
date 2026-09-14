package com.mosaicglobal.finance.modules.identity.domain.exception;

import com.mosaicglobal.finance.shared.kernel.DomainException;
import com.mosaicglobal.finance.shared.kernel.ErrorCategory;

public class InvalidRefreshTokenException extends DomainException {

  public static final String CODE_INVALID = "identity.invalid_refresh_token";
  public static final String CODE_EXPIRED = "identity.refresh_token_expired";
  public static final String CODE_REUSED = "identity.refresh_token_reused";

  private InvalidRefreshTokenException(String code, String message) {
    super(ErrorCategory.AUTHENTICATION, code, message);
  }

  public static InvalidRefreshTokenException unknown() {
    return new InvalidRefreshTokenException(CODE_INVALID, "Refresh token is not valid");
  }

  public static InvalidRefreshTokenException expired() {
    return new InvalidRefreshTokenException(CODE_EXPIRED, "Refresh token has expired");
  }

  public static InvalidRefreshTokenException reused() {
    return new InvalidRefreshTokenException(
        CODE_REUSED, "Refresh token was already used; all sessions of this device were revoked");
  }
}
