package com.mosaicglobal.finance.modules.identity.domain.model;

import com.mosaicglobal.finance.shared.kernel.Ensure;

/** Hash of a refresh token. The raw token is only ever held by the client. */
public record TokenHash(String value) {

  public TokenHash {
    Ensure.notBlank(value, "tokenHash");
  }

  @Override
  public String toString() {
    return "TokenHash[***]";
  }
}
