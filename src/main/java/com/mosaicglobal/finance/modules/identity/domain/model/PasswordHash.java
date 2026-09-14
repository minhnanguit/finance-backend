package com.mosaicglobal.finance.modules.identity.domain.model;

import com.mosaicglobal.finance.shared.kernel.Ensure;

/** Opaque encoded password (algorithm is the hasher's concern). */
public record PasswordHash(String value) {

  public PasswordHash {
    Ensure.notBlank(value, "passwordHash");
  }

  @Override
  public String toString() {
    return "PasswordHash[***]";
  }
}
