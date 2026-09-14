package com.mosaicglobal.finance.modules.identity.domain.model;

import com.mosaicglobal.finance.shared.kernel.Ensure;

/** Clear-text password in transit. Never logged, never stored; hashed by an outbound port. */
public record RawPassword(String value) {

  public static final int MIN_LENGTH = 8;
  public static final int MAX_LENGTH = 72; // bcrypt input limit

  public RawPassword {
    Ensure.notBlank(value, "password");
  }

  /** Applies the password policy – for registration and password changes. */
  public static RawPassword forRegistration(String raw) {
    Ensure.lengthBetween(raw, MIN_LENGTH, MAX_LENGTH, "password");
    return new RawPassword(raw);
  }

  /** No policy check – a wrong password must simply fail verification, not validation. */
  public static RawPassword forVerification(String raw) {
    Ensure.maxLength(raw, MAX_LENGTH, "password");
    return new RawPassword(raw);
  }

  @Override
  public String toString() {
    return "RawPassword[***]";
  }
}
