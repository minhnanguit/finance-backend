package com.mosaicglobal.finance.modules.identity.domain.model;

import com.mosaicglobal.finance.shared.kernel.Ensure;

/**
 * Stable, opaque identifier of the account at the identity provider (the OIDC {@code sub} claim).
 *
 * <p>The only link between a local user and Keycloak. Business tables never reference it — they
 * reference {@link com.mosaicglobal.finance.shared.kernel.UserId}, so the provider can be replaced
 * without migrating them (ADR-004).
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
