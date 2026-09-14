package com.mosaicglobal.finance.shared.kernel;

import java.util.Objects;

/**
 * Base class for every business failure.
 *
 * <p>{@code code} is a stable, machine-readable identifier (e.g. {@code identity.email_taken}) that
 * clients can branch on; {@code getMessage()} is a human-readable detail that may change.
 */
public abstract class DomainException extends RuntimeException {

  private final ErrorCategory category;
  private final String code;

  protected DomainException(ErrorCategory category, String code, String message) {
    super(message);
    this.category = Objects.requireNonNull(category, "category");
    this.code = Ensure.notBlank(code, "code");
  }

  public ErrorCategory category() {
    return category;
  }

  public String code() {
    return code;
  }
}
