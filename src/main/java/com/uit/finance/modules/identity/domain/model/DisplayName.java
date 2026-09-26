package com.uit.finance.modules.identity.domain.model;

import com.uit.finance.shared.kernel.Ensure;

public record DisplayName(String value) {

  public DisplayName {
    Ensure.lengthBetween(value, 1, 100, "displayName");
  }

  public static DisplayName of(String raw) {
    return new DisplayName(Ensure.notBlank(raw, "displayName").trim());
  }

  @Override
  public String toString() {
    return value;
  }
}
