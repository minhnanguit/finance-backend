package com.mosaicglobal.finance.modules.identity.domain.model;

import com.mosaicglobal.finance.shared.kernel.Ensure;

/**
 * Claim {@code sub} của Keycloak — link duy nhất giữa local user và Keycloak. Business table không
 * bao giờ reference nó mà reference {@link com.mosaicglobal.finance.shared.kernel.UserId}, để đổi
 * IdP không phải migrate (ADR-004).
 */
public record ExternalSubject(String value) {

  private static final int MAX_LENGTH = 255;

  public ExternalSubject {
    Ensure.notBlank(value, "externalSubject");
    Ensure.maxLength(value, MAX_LENGTH, "externalSubject");
  }

  public static ExternalSubject of(String raw) {
    return new ExternalSubject(Ensure.notBlank(raw, "externalSubject").trim());
  }

  @Override
  public String toString() {
    return value;
  }
}
