package com.mosaicglobal.finance.modules.identity.domain.model;

import com.mosaicglobal.finance.modules.identity.domain.event.UserRegistered;
import com.mosaicglobal.finance.shared.kernel.AggregateRoot;
import com.mosaicglobal.finance.shared.kernel.Ensure;
import com.mosaicglobal.finance.shared.kernel.UserId;
import java.time.Instant;

/**
 * Aggregate root: the local record of a person who signs in through the identity provider.
 *
 * <p>Holds no credentials. Passwords, sessions and refresh tokens are Keycloak's concern (ADR-004);
 * what lives here is the id every other module keys off, plus a cached copy of the profile.
 */
public final class User extends AggregateRoot {

  private final UserId id;
  private final ExternalSubject externalSubject;
  private final Email email;
  private final DisplayName displayName;
  private final UserStatus status;
  private final Instant createdAt;

  private User(
      UserId id,
      ExternalSubject externalSubject,
      Email email,
      DisplayName displayName,
      UserStatus status,
      Instant createdAt) {
    this.id = Ensure.notNull(id, "id");
    this.externalSubject = Ensure.notNull(externalSubject, "externalSubject");
    this.email = Ensure.notNull(email, "email");
    this.displayName = Ensure.notNull(displayName, "displayName");
    this.status = Ensure.notNull(status, "status");
    this.createdAt = Ensure.notNull(createdAt, "createdAt");
  }

  /** First sight of an authenticated subject: creates the local account and records the event. */
  public static User provision(
      UserId id,
      ExternalSubject externalSubject,
      Email email,
      DisplayName displayName,
      Instant now) {
    User user = new User(id, externalSubject, email, displayName, UserStatus.ACTIVE, now);
    user.registerEvent(UserRegistered.from(user, now));
    return user;
  }

  /** Reconstitutes an existing account from storage; records no events. */
  public static User rehydrate(
      UserId id,
      ExternalSubject externalSubject,
      Email email,
      DisplayName displayName,
      UserStatus status,
      Instant createdAt) {
    return new User(id, externalSubject, email, displayName, status, createdAt);
  }

  /**
   * Returns a copy carrying the profile the provider now reports, or {@code this} when nothing
   * changed — so callers can skip a pointless write.
   */
  public User withProfile(Email email, DisplayName displayName) {
    if (this.email.equals(email) && this.displayName.equals(displayName)) {
      return this;
    }
    return new User(id, externalSubject, email, displayName, status, createdAt);
  }

  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  public UserId getId() {
    return id;
  }

  public ExternalSubject getExternalSubject() {
    return externalSubject;
  }

  public Email getEmail() {
    return email;
  }

  public DisplayName getDisplayName() {
    return displayName;
  }

  public UserStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
