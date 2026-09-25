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
   * {@code ON CONFLICT DO NOTHING}: hai concurrent request đầu tiên cho cùng một {@code sub} đều
   * thành công ở DB — một bên insert, bên kia nhận 0 row rồi đọc lại — thay vì một bên làm
   * transaction bị rollback.
   *
   * @return 1 nếu statement này insert được row, 0 nếu {@code sub} đã tồn tại
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
