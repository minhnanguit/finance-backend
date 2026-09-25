package com.mosaicglobal.finance.modules.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

  Optional<UserJpaEntity> findByExternalSubject(String externalSubject);

  /**
   * Insert that loses gracefully. {@code ON CONFLICT DO NOTHING} lets two concurrent first-ever
   * requests for one subject both succeed at the database level — one inserts, the other gets 0
   * rows and reads the winner — instead of one of them poisoning its transaction.
   *
   * @return 1 when this statement created the row, 0 when the subject was already taken
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO users (id, external_subject, email, display_name, status,
                             version, created_at, updated_at)
          VALUES (:id, :externalSubject, :email, :displayName, :status, 0, :now, :now)
          ON CONFLICT (external_subject) DO NOTHING
          """,
      nativeQuery = true)
  int insertIfAbsent(
      @Param("id") UUID id,
      @Param("externalSubject") String externalSubject,
      @Param("email") String email,
      @Param("displayName") String displayName,
      @Param("status") String status,
      @Param("now") Instant now);
}
