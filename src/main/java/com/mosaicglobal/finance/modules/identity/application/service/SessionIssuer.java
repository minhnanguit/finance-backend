package com.mosaicglobal.finance.modules.identity.application.service;

import com.mosaicglobal.finance.modules.identity.application.port.in.AuthTokens;
import com.mosaicglobal.finance.modules.identity.application.port.out.AccessTokenIssuerPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.AccessTokenIssuerPort.IssuedAccessToken;
import com.mosaicglobal.finance.modules.identity.application.port.out.RefreshTokenGeneratorPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.RefreshTokenGeneratorPort.GeneratedRefreshToken;
import com.mosaicglobal.finance.modules.identity.application.port.out.SaveRefreshTokenPort;
import com.mosaicglobal.finance.modules.identity.domain.model.Device;
import com.mosaicglobal.finance.modules.identity.domain.model.RefreshToken;
import com.mosaicglobal.finance.modules.identity.domain.model.RefreshTokenId;
import com.mosaicglobal.finance.modules.identity.domain.model.User;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Shared by register / login / refresh: mints the token pair for a device. */
@Component
class SessionIssuer {

  private final RefreshTokenGeneratorPort refreshTokens;
  private final AccessTokenIssuerPort accessTokens;
  private final SaveRefreshTokenPort saveRefreshToken;
  private final SessionSettings settings;

  SessionIssuer(
      RefreshTokenGeneratorPort refreshTokens,
      AccessTokenIssuerPort accessTokens,
      SaveRefreshTokenPort saveRefreshToken,
      SessionSettings settings) {
    this.refreshTokens = refreshTokens;
    this.accessTokens = accessTokens;
    this.saveRefreshToken = saveRefreshToken;
    this.settings = settings;
  }

  AuthTokens open(User user, Device device, Instant now) {
    GeneratedRefreshToken generated = refreshTokens.generate();
    RefreshToken token =
        RefreshToken.issue(
            RefreshTokenId.newId(),
            user.getId(),
            device,
            generated.hash(),
            now,
            settings.refreshTokenTtl());
    saveRefreshToken.save(token);
    return pair(user, device, generated);
  }

  AuthTokens rotate(User user, RefreshToken presented, Instant now) {
    GeneratedRefreshToken generated = refreshTokens.generate();
    RefreshToken successor =
        presented.rotate(RefreshTokenId.newId(), generated.hash(), now, settings.refreshTokenTtl());
    saveRefreshToken.save(presented);
    saveRefreshToken.save(successor);
    return pair(user, presented.getDevice(), generated);
  }

  private AuthTokens pair(User user, Device device, GeneratedRefreshToken refresh) {
    IssuedAccessToken access = accessTokens.issue(user.getId(), device.id());
    return new AuthTokens(access.value(), refresh.rawValue(), access.ttl());
  }
}
