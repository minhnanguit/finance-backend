package com.uit.finance.shared.sync;

import com.uit.finance.shared.kernel.DomainException;
import com.uit.finance.shared.kernel.ErrorCategory;

/** {@code data} của op sai hình dạng. Message chỉ có tên field, không có giá trị (ADR-006 B8). */
public final class SyncDataException extends DomainException {

  public static final String UNKNOWN_FIELD = "sync.unknown_field";
  public static final String INVALID_FIELD = "sync.invalid_field";

  private SyncDataException(String code, String message) {
    super(ErrorCategory.VALIDATION, code, message);
  }

  static SyncDataException unknown(String field) {
    return new SyncDataException(UNKNOWN_FIELD, "Field '" + field + "' is not allowed here");
  }

  static SyncDataException invalid(String field) {
    return new SyncDataException(INVALID_FIELD, "Field '" + field + "' is missing or malformed");
  }
}
