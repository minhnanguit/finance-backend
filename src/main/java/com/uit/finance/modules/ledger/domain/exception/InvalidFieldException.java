package com.uit.finance.modules.ledger.domain.exception;

import com.uit.finance.shared.kernel.ErrorCategory;

/** Field thiếu, sai định dạng hoặc ngoài giới hạn. Chỉ kèm tên field, không kèm giá trị (B8). */
public class InvalidFieldException extends LedgerException {

  public static final String CODE = "ledger.invalid_field";

  private final String field;

  public InvalidFieldException(String field) {
    super(
        ErrorCategory.VALIDATION,
        CODE,
        "Field '" + field + "' is missing, malformed or out of range");
    this.field = field;
  }

  public String field() {
    return field;
  }
}
