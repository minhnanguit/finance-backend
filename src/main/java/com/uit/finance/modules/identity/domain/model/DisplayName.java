package com.uit.finance.modules.identity.domain.model;

import com.uit.finance.shared.kernel.Ensure;

public record DisplayName(String value) {

  public DisplayName {
    Ensure.lengthBetween(value, 1, 100, "displayName");
  }

  /** Tên thay thế cho tài khoản đã xoá (ADR-008 A4). */
  public static final DisplayName ERASED = new DisplayName("Deleted user");

  public static DisplayName of(String raw) {
    return new DisplayName(Ensure.notBlank(raw, "displayName").trim());
  }

  @Override
  public String toString() {
    return value;
  }
}
