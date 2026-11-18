package com.uit.finance.modules.identity.domain.model;

import com.uit.finance.modules.identity.domain.event.UserDeleted;
import com.uit.finance.modules.identity.domain.event.UserDeletionRequested;
import com.uit.finance.modules.identity.domain.event.UserRegistered;
import com.uit.finance.modules.identity.domain.exception.UserDeletedException;
import com.uit.finance.shared.kernel.AggregateRoot;
import com.uit.finance.shared.kernel.Ensure;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

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
  private final @Nullable Instant deletionRequestedAt;

  private User(
      UserId id,
      ExternalSubject externalSubject,
      Email email,
      DisplayName displayName,
      UserStatus status,
      Instant createdAt,
      @Nullable Instant deletionRequestedAt) {
    this.id = Ensure.notNull(id, "id");
    this.externalSubject = Ensure.notNull(externalSubject, "externalSubject");
    this.email = Ensure.notNull(email, "email");
    this.displayName = Ensure.notNull(displayName, "displayName");
    this.status = Ensure.notNull(status, "status");
    this.createdAt = Ensure.notNull(createdAt, "createdAt");
    if (status.isDeleted() && deletionRequestedAt == null) {
      throw new IllegalArgumentException("a deleted user needs deletionRequestedAt");
    }
    this.deletionRequestedAt = deletionRequestedAt;
  }

  /** Lần đầu thấy {@code sub}: tạo account và register event {@link UserRegistered}. */
  public static User provision(
      UserId id,
      ExternalSubject externalSubject,
      Email email,
      DisplayName displayName,
      Instant now) {
    User user = new User(id, externalSubject, email, displayName, UserStatus.ACTIVE, now, null);
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
      Instant createdAt,
      @Nullable Instant deletionRequestedAt) {
    return new User(
        id, externalSubject, email, displayName, status, createdAt, deletionRequestedAt);
  }

  /**
   * Trả về chính {@code this} khi không có gì đổi, để caller skip write thừa.
   *
   * @throws UserDeletedException tài khoản đã xoá thì không nhận profile mới từ token còn hạn
   */
  public User withProfile(Email email, DisplayName displayName) {
    requireNotDeleted();
    if (this.email.equals(email) && this.displayName.equals(displayName)) {
      return this;
    }
    return new User(
        id, externalSubject, email, displayName, status, createdAt, deletionRequestedAt);
  }

  /**
   * Bắt đầu xoá tài khoản (ADR-008 A4): xoá thông tin cá nhân ngay, giữ {@code sub} để token còn
   * hạn không tạo lại được tài khoản, và register {@link UserDeletionRequested}.
   *
   * @throws UserDeletedException nếu đã xoá rồi
   */
  public User requestDeletion(Instant now) {
    requireNotDeleted();
    User deleting =
        new User(
            id,
            externalSubject,
            Email.erasedFor(id),
            DisplayName.ERASED,
            UserStatus.DELETING,
            createdAt,
            now);
    deleting.registerEvent(UserDeletionRequested.of(id, now));
    return deleting;
  }

  /**
   * Hết thời gian chờ: báo mọi module xoá thật dữ liệu ({@link UserDeleted}). Chỉ từ {@code
   * DELETING}.
   */
  public User completeDeletion(Instant now) {
    if (status != UserStatus.DELETING) {
      throw new IllegalStateException("only a user being deleted can complete deletion");
    }
    User deleted =
        new User(
            id,
            externalSubject,
            email,
            displayName,
            UserStatus.DELETED,
            createdAt,
            deletionRequestedAt);
    deleted.registerEvent(UserDeleted.of(id, now));
    return deleted;
  }

  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  /** {@code DELETING} hoặc {@code DELETED}: không còn dùng được. */
  public boolean isDeleted() {
    return status.isDeleted();
  }

  /**
   * @throws UserDeletedException khi tài khoản đã xoá
   */
  public void requireNotDeleted() {
    if (isDeleted()) {
      throw new UserDeletedException();
    }
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

  /** Lúc user yêu cầu xoá; {@code null} khi tài khoản còn dùng. */
  public @Nullable Instant getDeletionRequestedAt() {
    return deletionRequestedAt;
  }
}
