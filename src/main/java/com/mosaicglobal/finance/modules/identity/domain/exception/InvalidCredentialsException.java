package com.mosaicglobal.finance.modules.identity.domain.exception;

import com.mosaicglobal.finance.shared.kernel.DomainException;
import com.mosaicglobal.finance.shared.kernel.ErrorCategory;

/** Deliberately vague: never reveals whether the e-mail exists or the account is disabled. */
public class InvalidCredentialsException extends DomainException {

  public static final String CODE = "identity.invalid_credentials";

  public InvalidCredentialsException() {
    super(ErrorCategory.AUTHENTICATION, CODE, "Invalid e-mail or password");
  }
}
