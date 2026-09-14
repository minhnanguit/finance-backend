package com.mosaicglobal.finance.modules.identity.application.service;

import com.mosaicglobal.finance.modules.identity.application.port.in.RevokeSessionUseCase;
import com.mosaicglobal.finance.modules.identity.application.port.out.LoadRefreshTokenPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.RefreshTokenGeneratorPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.SaveRefreshTokenPort;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class RevokeSessionService implements RevokeSessionUseCase {

  private final LoadRefreshTokenPort loadRefreshToken;
  private final SaveRefreshTokenPort saveRefreshToken;
  private final RefreshTokenGeneratorPort refreshTokens;
  private final Clock clock;

  RevokeSessionService(
      LoadRefreshTokenPort loadRefreshToken,
      SaveRefreshTokenPort saveRefreshToken,
      RefreshTokenGeneratorPort refreshTokens,
      Clock clock) {
    this.loadRefreshToken = loadRefreshToken;
    this.saveRefreshToken = saveRefreshToken;
    this.refreshTokens = refreshTokens;
    this.clock = clock;
  }

  @Override
  public void revoke(RevokeSessionCommand command) {
    loadRefreshToken
        .byHash(refreshTokens.hash(command.refreshToken()))
        .filter(token -> token.belongsTo(command.requester()))
        .ifPresent(
            token -> {
              token.revoke(clock.instant());
              saveRefreshToken.save(token);
            });
  }
}
