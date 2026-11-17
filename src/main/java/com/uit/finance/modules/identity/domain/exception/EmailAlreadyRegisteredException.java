package com.uit.finance.modules.identity.domain.exception;

import com.uit.finance.shared.kernel.DomainException;
import com.uit.finance.shared.kernel.ErrorCategory;

public class EmailAlreadyRegisteredException extends DomainException {

  public static final String CODE = "identity.email_taken";

  public EmailAlreadyRegisteredException() {
    super(ErrorCategory.CONFLICT, CODE, "An account with this e-mail already exists");
  }
}
