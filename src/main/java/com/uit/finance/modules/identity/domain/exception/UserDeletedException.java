package com.uit.finance.modules.identity.domain.exception;

import com.uit.finance.shared.kernel.DomainException;
import com.uit.finance.shared.kernel.ErrorCategory;

/**
 * Tài khoản đã bị xoá (ADR-008 A6). App nhận mã này thì xoá dữ liệu trên máy và về màn đăng nhập,
 * không thử refresh hay gửi lại.
 */
public class UserDeletedException extends DomainException {

  public static final String CODE = "identity.user_deleted";

  public UserDeletedException() {
    super(ErrorCategory.FORBIDDEN, CODE, "This account has been deleted");
  }
}
