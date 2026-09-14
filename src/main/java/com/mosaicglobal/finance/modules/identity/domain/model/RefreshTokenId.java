package com.mosaicglobal.finance.modules.identity.domain.model;

import com.mosaicglobal.finance.shared.kernel.Ensure;
import java.util.UUID;

public record RefreshTokenId(UUID value) {

  public RefreshTokenId {
    Ensure.notNull(value, "refreshTokenId");
  }

  public static RefreshTokenId newId() {
    return new RefreshTokenId(UUID.randomUUID());
  }
}
