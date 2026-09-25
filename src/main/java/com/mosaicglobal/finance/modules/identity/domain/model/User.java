package com.mosaicglobal.finance.modules.identity.domain.model;

import com.mosaicglobal.finance.modules.identity.domain.event.UserRegistered;
import com.mosaicglobal.finance.shared.kernel.AggregateRoot;
import com.mosaicglobal.finance.shared.kernel.Ensure;
import com.mosaicglobal.finance.shared.kernel.UserId;
import java.time.Instant;

/**
 * Local record của user login qua Keycloak. Không giữ password hay token (ADR-004) — chỉ giữ id mà
 * mọi module khác reference tới, cùng bản copy của profile.
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

  /** Lần đầu thấy {@code sub}: tạo account và register event {@link UserRegistered}. */
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

  /** Rehydrate từ DB, không register event. */
  public static User rehydrate(
      UserId id,
      ExternalSubject externalSubject,
      Email email,
      DisplayName displayName,
      UserStatus status,
      Instant createdAt) {
    return new User(id, externalSubject, email, displayName, status, createdAt);
  }

  /** Trả về chính {@code this} khi không có gì đổi, để caller skip write thừa. */
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
