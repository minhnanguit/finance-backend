package com.uit.finance.modules.identity.adapter.out.persistence;

import com.uit.finance.modules.identity.domain.model.UserStatus;
import com.uit.finance.shared.persistence.AbstractJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "users")
class UserJpaEntity extends AbstractJpaEntity {

  @Column(name = "external_subject", nullable = false, updatable = false, length = 255)
  private String externalSubject;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserStatus status;

  @Column(name = "deletion_requested_at")
  private @Nullable Instant deletionRequestedAt;

  protected UserJpaEntity() {}

  UserJpaEntity(
      UUID id,
      String externalSubject,
      String email,
      String displayName,
      UserStatus status,
      Instant createdAt) {
    super(id, createdAt);
    this.externalSubject = externalSubject;
    this.email = email;
    this.displayName = displayName;
    this.status = status;
  }

  String getExternalSubject() {
    return externalSubject;
  }

  String getEmail() {
    return email;
  }

  String getDisplayName() {
    return displayName;
  }

  UserStatus getStatus() {
    return status;
  }

  @Nullable Instant getDeletionRequestedAt() {
    return deletionRequestedAt;
  }

  /** Không có {@code externalSubject}: đó là link tới account Keycloak, immutable. */
  void applyState(
      String email, String displayName, UserStatus status, @Nullable Instant deletionRequestedAt) {
    this.email = email;
    this.displayName = displayName;
    this.status = status;
    this.deletionRequestedAt = deletionRequestedAt;
  }
}
