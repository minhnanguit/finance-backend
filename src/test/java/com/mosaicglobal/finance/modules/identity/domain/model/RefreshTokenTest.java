package com.mosaicglobal.finance.modules.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mosaicglobal.finance.modules.identity.domain.exception.InvalidRefreshTokenException;
import com.mosaicglobal.finance.shared.kernel.DomainException;
import com.mosaicglobal.finance.shared.kernel.UserId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

  private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");
  private static final Duration TTL = Duration.ofDays(30);
  private static final Device DEVICE =
      new Device(new DeviceId("device-0001"), "Pixel 9", DevicePlatform.ANDROID);

  private RefreshToken issued() {
    return RefreshToken.issue(
        RefreshTokenId.newId(), UserId.newId(), DEVICE, new TokenHash("h1"), NOW, TTL);
  }

  @Test
  void rotationRevokesTheOldTokenAndLinksTheSuccessor() {
    RefreshToken old = issued();
    Instant later = NOW.plusSeconds(60);

    RefreshToken next = old.rotate(RefreshTokenId.newId(), new TokenHash("h2"), later, TTL);

    assertThat(old.isRevoked()).isTrue();
    assertThat(old.getReplacedBy()).isEqualTo(next.getId());
    assertThat(next.isActive(later)).isTrue();
    assertThat(next.getUserId()).isEqualTo(old.getUserId());
    assertThat(next.getDevice()).isEqualTo(DEVICE);
    assertThat(next.getExpiresAt()).isEqualTo(later.plus(TTL));
  }

  @Test
  void rotatingARevokedTokenIsReuse() {
    RefreshToken token = issued();
    token.revoke(NOW);

    assertThatThrownBy(() -> token.rotate(RefreshTokenId.newId(), new TokenHash("h2"), NOW, TTL))
        .isInstanceOf(InvalidRefreshTokenException.class)
        .satisfies(
            e ->
                assertThat(((DomainException) e).code())
                    .isEqualTo(InvalidRefreshTokenException.CODE_REUSED));
  }

  @Test
  void expiredTokenCannotBeRotated() {
    RefreshToken token = issued();
    Instant afterExpiry = NOW.plus(TTL);

    assertThat(token.isExpired(afterExpiry)).isTrue();
    assertThatThrownBy(
            () -> token.rotate(RefreshTokenId.newId(), new TokenHash("h2"), afterExpiry, TTL))
        .satisfies(
            e ->
                assertThat(((DomainException) e).code())
                    .isEqualTo(InvalidRefreshTokenException.CODE_EXPIRED));
  }

  @Test
  void revokeIsIdempotent() {
    RefreshToken token = issued();
    token.revoke(NOW);
    token.revoke(NOW.plusSeconds(5));
    assertThat(token.getRevokedAt()).isEqualTo(NOW);
  }
}
