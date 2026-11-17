package com.uit.finance.modules.identity.domain.exception;

import com.uit.finance.shared.kernel.DomainException;
import com.uit.finance.shared.kernel.ErrorCategory;

/**
 * Phải đăng nhập lại ngay trước thao tác này (ADR-008 A2). App mở Keycloak với {@code
 * prompt=login}.
 */
public class ReauthenticationRequiredException extends DomainException {

  public static final String CODE = "identity.reauth_required";

  public ReauthenticationRequiredException() {
    super(ErrorCategory.FORBIDDEN, CODE, "Sign in again right before deleting the account");
  }
}
