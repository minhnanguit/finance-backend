package com.mosaicglobal.finance.modules.identity.domain.model;

import com.mosaicglobal.finance.modules.identity.domain.event.UserRegistered;
import com.mosaicglobal.finance.modules.identity.domain.exception.InvalidCredentialsException;
import com.mosaicglobal.finance.shared.kernel.AggregateRoot;
import com.mosaicglobal.finance.shared.kernel.Ensure;
import com.mosaicglobal.finance.shared.kernel.UserId;
import java.time.Instant;

/** Aggregate root: an account that can authenticate. */
public final class User extends AggregateRoot {

  private final UserId id;
  private final Email email;
  private final PasswordHash passwordHash;
  private final DisplayName displayName;
  private final UserStatus status;
  private final Instant createdAt;

  private User(
      UserId id,
      Email email,
      PasswordHash passwordHash,
      DisplayName displayName,
      UserStatus status,
      Instant createdAt) {
    this.id = Ensure.notNull(id, "id");
    this.email = Ensure.notNull(email, "email");
    this.passwordHash = Ensure.notNull(passwordHash, "passwordHash");
    this.displayName = Ensure.notNull(displayName, "displayName");
    this.status = Ensure.notNull(status, "status");
    this.createdAt = Ensure.notNull(createdAt, "createdAt");
  }

  /** Creates a new active account and records {@link UserRegistered}. */
  public static User register(
      UserId id, Email email, PasswordHash passwordHash, DisplayName displayName, Instant now) {
    User user = new User(id, email, passwordHash, displayName, UserStatus.ACTIVE, now);
    user.registerEvent(UserRegistered.from(user, now));
    return user;
  }

  /** Reconstitutes an existing account from storage; records no events. */
  public static User rehydrate(
      UserId id,
      Email email,
      PasswordHash passwordHash,
      DisplayName displayName,
      UserStatus status,
      Instant createdAt) {
    return new User(id, email, passwordHash, displayName, status, createdAt);
  }

  /** Fails with the same error as a wrong password so account state is not leaked. */
  public void ensureCanAuthenticate() {
    if (status != UserStatus.ACTIVE) {
      throw new InvalidCredentialsException();
    }
  }

  public UserId getId() {
    return id;
  }

  public Email getEmail() {
    return email;
  }

  public PasswordHash getPasswordHash() {
    return passwordHash;
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
