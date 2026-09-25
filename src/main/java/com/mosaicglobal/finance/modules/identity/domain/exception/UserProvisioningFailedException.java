package com.mosaicglobal.finance.modules.identity.domain.exception;

import com.mosaicglobal.finance.shared.kernel.DomainException;
import com.mosaicglobal.finance.shared.kernel.ErrorCategory;

/**
 * The account could neither be inserted nor found afterwards. Only reachable if the row vanished
 * between the two statements, so it signals a broken invariant rather than user error.
 */
public class UserProvisioningFailedException extends DomainException {

  public static final String CODE = "identity.provisioning_failed";

  public UserProvisioningFailedException(String externalSubject) {
    super(
        ErrorCategory.BUSINESS_RULE,
        CODE,
        "Could not provision a local account for subject " + externalSubject);
  }
}
