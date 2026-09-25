package com.mosaicglobal.finance.modules.identity.adapter.out.persistence;

import com.mosaicglobal.finance.modules.identity.domain.model.UserStatus;
import com.mosaicglobal.finance.shared.persistence.AbstractJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

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

  /** The external subject is immutable: it is the link to the IdP account, not a profile field. */
  void applyProfile(String email, String displayName, UserStatus status) {
    this.email = email;
    this.displayName = displayName;
    this.status = status;
  }
}
