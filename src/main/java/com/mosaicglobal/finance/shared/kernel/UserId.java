package com.mosaicglobal.finance.shared.kernel;

import java.util.UUID;

/** Identity of a user. Lives in the kernel because every module scopes its data by user. */
public record UserId(UUID value) {

  public UserId {
    Ensure.notNull(value, "userId");
  }

  public static UserId newId() {
    return new UserId(UUID.randomUUID());
  }

  public static UserId of(String value) {
    return new UserId(UUID.fromString(value));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
