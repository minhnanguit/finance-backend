package com.mosaicglobal.finance.modules.identity.application.service;

import com.mosaicglobal.finance.modules.identity.application.port.in.AuthTokens;
import com.mosaicglobal.finance.modules.identity.application.port.in.RefreshSessionUseCase;
import com.mosaicglobal.finance.modules.identity.application.port.out.LoadRefreshTokenPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.LoadUserPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.RefreshTokenGeneratorPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.RevokeDeviceSessionsPort;
import com.mosaicglobal.finance.modules.identity.domain.exception.InvalidRefreshTokenException;
import com.mosaicglobal.finance.modules.identity.domain.model.DeviceId;
import com.mosaicglobal.finance.modules.identity.domain.model.RefreshToken;
import com.mosaicglobal.finance.modules.identity.domain.model.User;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh-token rotation with reuse detection: a token that was already rotated is proof that
 * either the client or an attacker holds a stale copy, so every session of that device is revoked.
 *
 * <p>{@code noRollbackFor}: the reuse branch writes (revokes the device) and then fails the request
 * on purpose; that write must survive the exception. The other failure branches write nothing.
 */
@Service
@Transactional(noRollbackFor = InvalidRefreshTokenException.class)
class RefreshSessionService implements RefreshSessionUseCase {

  private static final Logger log = LoggerFactory.getLogger(RefreshSessionService.class);

  private final LoadRefreshTokenPort loadRefreshToken;
  private final RevokeDeviceSessionsPort revokeDeviceSessions;
  private final RefreshTokenGeneratorPort refreshTokens;
  private final LoadUserPort loadUser;
  private final SessionIssuer sessions;
  private final Clock clock;

  RefreshSessionService(
      LoadRefreshTokenPort loadRefreshToken,
      RevokeDeviceSessionsPort revokeDeviceSessions,
      RefreshTokenGeneratorPort refreshTokens,
      LoadUserPort loadUser,
      SessionIssuer sessions,
      Clock clock) {
    this.loadRefreshToken = loadRefreshToken;
    this.revokeDeviceSessions = revokeDeviceSessions;
    this.refreshTokens = refreshTokens;
    this.loadUser = loadUser;
    this.sessions = sessions;
    this.clock = clock;
  }

  @Override
  public AuthTokens refresh(RefreshSessionCommand command) {
    DeviceId deviceId = new DeviceId(command.deviceId());
    RefreshToken presented =
        loadRefreshToken
            .byHash(refreshTokens.hash(command.refreshToken()))
            .orElseThrow(InvalidRefreshTokenException::unknown);
    if (!presented.isBoundTo(deviceId)) {
      throw InvalidRefreshTokenException.unknown();
    }

    Instant now = clock.instant();
    if (presented.isRevoked()) {
      int revoked = revokeDeviceSessions.revokeAllForDevice(presented.getUserId(), deviceId, now);
      log.warn(
          "Refresh token reuse detected for user {} device {}; revoked {} session(s)",
          presented.getUserId(),
          deviceId,
          revoked);
      throw InvalidRefreshTokenException.reused();
    }

    User user =
        loadUser.byId(presented.getUserId()).orElseThrow(InvalidRefreshTokenException::unknown);
    user.ensureCanAuthenticate();

    return sessions.rotate(user, presented, now);
  }
}
