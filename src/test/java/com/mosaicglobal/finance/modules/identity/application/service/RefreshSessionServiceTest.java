package com.mosaicglobal.finance.modules.identity.application.service;

import static com.mosaicglobal.finance.modules.identity.application.service.IdentityFakes.CLOCK;
import static com.mosaicglobal.finance.modules.identity.application.service.IdentityFakes.DEVICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mosaicglobal.finance.modules.identity.application.port.in.AuthTokens;
import com.mosaicglobal.finance.modules.identity.application.port.in.RefreshSessionUseCase.RefreshSessionCommand;
import com.mosaicglobal.finance.modules.identity.application.service.IdentityFakes.InMemoryRefreshTokens;
import com.mosaicglobal.finance.modules.identity.application.service.IdentityFakes.InMemoryUsers;
import com.mosaicglobal.finance.modules.identity.application.service.IdentityFakes.SequentialRefreshTokenGenerator;
import com.mosaicglobal.finance.modules.identity.domain.exception.InvalidRefreshTokenException;
import com.mosaicglobal.finance.modules.identity.domain.model.Device;
import com.mosaicglobal.finance.modules.identity.domain.model.DisplayName;
import com.mosaicglobal.finance.modules.identity.domain.model.Email;
import com.mosaicglobal.finance.modules.identity.domain.model.PasswordHash;
import com.mosaicglobal.finance.modules.identity.domain.model.RefreshToken;
import com.mosaicglobal.finance.modules.identity.domain.model.User;
import com.mosaicglobal.finance.modules.identity.domain.model.UserStatus;
import com.mosaicglobal.finance.shared.kernel.DomainException;
import com.mosaicglobal.finance.shared.kernel.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RefreshSessionServiceTest {

  private InMemoryRefreshTokens refreshTokens;
  private SequentialRefreshTokenGenerator generator;
  private User alice;
  private RefreshSessionService service;
  private SessionIssuer issuer;

  @BeforeEach
  void setUp() {
    InMemoryUsers users = new InMemoryUsers();
    refreshTokens = new InMemoryRefreshTokens();
    generator = new SequentialRefreshTokenGenerator();
    issuer = IdentityFakes.sessionIssuer(generator, refreshTokens);
    alice =
        User.rehydrate(
            UserId.newId(),
            Email.of("alice@example.com"),
            new PasswordHash("hashed:x"),
            DisplayName.of("Alice"),
            UserStatus.ACTIVE,
            IdentityFakes.NOW);
    users.save(alice);
    service =
        new RefreshSessionService(refreshTokens, refreshTokens, generator, users, issuer, CLOCK);
  }

  private AuthTokens openSession() {
    return issuer.open(alice, DeviceMapper.toDevice(DEVICE), IdentityFakes.NOW);
  }

  @Test
  void rotatesTheTokenAndRevokesTheOldOne() {
    AuthTokens first = openSession();

    AuthTokens second =
        service.refresh(new RefreshSessionCommand(first.refreshToken(), DEVICE.deviceId()));

    assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
    RefreshToken old = refreshTokens.byHash(generator.hash(first.refreshToken())).orElseThrow();
    RefreshToken next = refreshTokens.byHash(generator.hash(second.refreshToken())).orElseThrow();
    assertThat(old.isRevoked()).isTrue();
    assertThat(old.getReplacedBy()).isEqualTo(next.getId());
    assertThat(next.isActive(IdentityFakes.NOW)).isTrue();
  }

  @Test
  void reusingARotatedTokenRevokesEverySessionOfTheDevice() {
    AuthTokens first = openSession();
    AuthTokens second =
        service.refresh(new RefreshSessionCommand(first.refreshToken(), DEVICE.deviceId()));

    assertThatThrownBy(
            () ->
                service.refresh(new RefreshSessionCommand(first.refreshToken(), DEVICE.deviceId())))
        .isInstanceOf(InvalidRefreshTokenException.class)
        .satisfies(
            e ->
                assertThat(((DomainException) e).code())
                    .isEqualTo(InvalidRefreshTokenException.CODE_REUSED));

    RefreshToken latest = refreshTokens.byHash(generator.hash(second.refreshToken())).orElseThrow();
    assertThat(latest.isRevoked()).as("the legitimate successor is revoked too").isTrue();
  }

  @Test
  void tokenPresentedFromAnotherDeviceIsRejected() {
    AuthTokens first = openSession();
    assertThatThrownBy(
            () ->
                service.refresh(new RefreshSessionCommand(first.refreshToken(), "other-device-1")))
        .satisfies(
            e ->
                assertThat(((DomainException) e).code())
                    .isEqualTo(InvalidRefreshTokenException.CODE_INVALID));
  }

  @Test
  void unknownTokenIsRejected() {
    assertThatThrownBy(() -> service.refresh(new RefreshSessionCommand("nope", DEVICE.deviceId())))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void sessionDeviceIsPreservedAcrossRotation() {
    AuthTokens first = openSession();
    AuthTokens second =
        service.refresh(new RefreshSessionCommand(first.refreshToken(), DEVICE.deviceId()));
    Device device =
        refreshTokens.byHash(generator.hash(second.refreshToken())).orElseThrow().getDevice();
    assertThat(device.id().value()).isEqualTo(DEVICE.deviceId());
  }
}
