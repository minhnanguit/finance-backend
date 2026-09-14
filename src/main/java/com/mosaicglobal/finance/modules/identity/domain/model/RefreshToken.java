package com.mosaicglobal.finance.modules.identity.domain.model;

import com.mosaicglobal.finance.modules.identity.domain.exception.InvalidRefreshTokenException;
import com.mosaicglobal.finance.shared.kernel.Ensure;
import com.mosaicglobal.finance.shared.kernel.UserId;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * One device session. Single use: presenting it produces a successor and revokes this one.
 * Presenting an already-rotated token is a reuse signal handled by the application layer.
 */
public final class RefreshToken {

  private final RefreshTokenId id;
  private final UserId userId;
  private final Device device;
  private final TokenHash tokenHash;
  private final Instant issuedAt;
  private final Instant expiresAt;
  private @Nullable Instant revokedAt;
  private @Nullable RefreshTokenId replacedBy;

  private RefreshToken(
      RefreshTokenId id,
      UserId userId,
      Device device,
      TokenHash tokenHash,
      Instant issuedAt,
      Instant expiresAt,
      @Nullable Instant revokedAt,
      @Nullable RefreshTokenId replacedBy) {
    this.id = Ensure.notNull(id, "id");
    this.userId = Ensure.notNull(userId, "userId");
    this.device = Ensure.notNull(device, "device");
    this.tokenHash = Ensure.notNull(tokenHash, "tokenHash");
    this.issuedAt = Ensure.notNull(issuedAt, "issuedAt");
    this.expiresAt = Ensure.notNull(expiresAt, "expiresAt");
    if (!expiresAt.isAfter(issuedAt)) {
      throw new IllegalArgumentException("expiresAt must be after issuedAt");
    }
    this.revokedAt = revokedAt;
    this.replacedBy = replacedBy;
  }

  public static RefreshToken issue(
      RefreshTokenId id, UserId userId, Device device, TokenHash hash, Instant now, Duration ttl) {
    return new RefreshToken(id, userId, device, hash, now, now.plus(ttl), null, null);
  }

  public static RefreshToken rehydrate(
      RefreshTokenId id,
      UserId userId,
      Device device,
      TokenHash tokenHash,
      Instant issuedAt,
      Instant expiresAt,
      @Nullable Instant revokedAt,
      @Nullable RefreshTokenId replacedBy) {
    return new RefreshToken(
        id, userId, device, tokenHash, issuedAt, expiresAt, revokedAt, replacedBy);
  }

  /** Revokes this token and returns its successor for the same device. */
  public RefreshToken rotate(
      RefreshTokenId successorId, TokenHash successorHash, Instant now, Duration ttl) {
    if (isRevoked()) {
      throw InvalidRefreshTokenException.reused();
    }
    if (isExpired(now)) {
      throw InvalidRefreshTokenException.expired();
    }
    RefreshToken successor = issue(successorId, userId, device, successorHash, now, ttl);
    this.revokedAt = now;
    this.replacedBy = successor.id;
    return successor;
  }

  public void revoke(Instant now) {
    if (revokedAt == null) {
      revokedAt = now;
    }
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public boolean isExpired(Instant now) {
    return !now.isBefore(expiresAt);
  }

  public boolean isActive(Instant now) {
    return !isRevoked() && !isExpired(now);
  }

  public boolean belongsTo(UserId candidate) {
    return userId.equals(candidate);
  }

  public boolean isBoundTo(DeviceId candidate) {
    return device.id().equals(candidate);
  }

  public RefreshTokenId getId() {
    return id;
  }

  public UserId getUserId() {
    return userId;
  }

  public Device getDevice() {
    return device;
  }

  public TokenHash getTokenHash() {
    return tokenHash;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public @Nullable Instant getRevokedAt() {
    return revokedAt;
  }

  public @Nullable RefreshTokenId getReplacedBy() {
    return replacedBy;
  }
}
