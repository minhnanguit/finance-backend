package com.mosaicglobal.finance.modules.identity.domain.exception;

import com.mosaicglobal.finance.shared.kernel.DomainException;
import com.mosaicglobal.finance.shared.kernel.ErrorCategory;

/**
 * Insert không được mà đọc lại cũng không thấy — chỉ xảy ra nếu row biến mất giữa hai statement.
 * Đây là invariant bị vỡ, không phải lỗi của user.
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
