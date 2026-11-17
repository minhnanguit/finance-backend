package com.uit.finance.modules.ledger.domain.model.shared;

/** Tên ví hoặc danh mục: cắt khoảng trắng 2 đầu, 1–50 ký tự (ADR-005 §4). */
public record LedgerName(String value) {

  public LedgerName {
    value = Fields.requiredText(value, LedgerLimits.MAX_NAME_LENGTH, "name");
  }

  /** Không in giá trị: tên ví có thể lộ thông tin cá nhân khi lọt vào log (ADR-006 B8). */
  @Override
  public String toString() {
    return "LedgerName[redacted]";
  }
}
