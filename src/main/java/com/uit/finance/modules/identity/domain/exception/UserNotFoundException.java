package com.uit.finance.modules.identity.domain.exception;

import com.uit.finance.shared.kernel.DomainException;
import com.uit.finance.shared.kernel.ErrorCategory;
import com.uit.finance.shared.kernel.UserId;

public class UserNotFoundException extends DomainException {

  public static final String CODE = "identity.user_not_found";

  public UserNotFoundException(UserId id) {
    super(ErrorCategory.NOT_FOUND, CODE, "User " + id + " does not exist");
  }
}
