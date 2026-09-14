package com.mosaicglobal.finance.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Base for every JPA entity in {@code adapter.out.persistence} packages. Domain models never extend
 * this class; adapters map between the two.
 *
 * <p>Provides the columns every user-data table has: client-generated UUID id, optimistic-lock
 * version, audit timestamps and soft-delete marker.
 */
@MappedSuperclass
public abstract class AbstractJpaEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private @Nullable Instant deletedAt;

  protected AbstractJpaEntity() {}

  protected AbstractJpaEntity(UUID id, Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = createdAt;
  }

  @PrePersist
  void onPersist() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public long getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public @Nullable Instant getDeletedAt() {
    return deletedAt;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public void markDeleted(Instant at) {
    this.deletedAt = at;
  }

  @Override
  public final boolean equals(Object o) {
    return this == o
        || (o instanceof AbstractJpaEntity other
            && getClass() == other.getClass()
            && id != null
            && id.equals(other.id));
  }

  @Override
  public final int hashCode() {
    return getClass().hashCode();
  }
}
