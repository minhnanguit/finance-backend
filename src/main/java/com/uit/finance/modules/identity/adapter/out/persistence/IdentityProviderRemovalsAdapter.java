package com.uit.finance.modules.identity.adapter.out.persistence;

import com.uit.finance.modules.identity.application.port.out.IdentityProviderRemovalsPort;
import com.uit.finance.modules.identity.domain.model.ExternalSubject;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Cột {@code users.idp_removed_at} không nằm trong {@link UserJpaEntity}: đó là trạng thái của việc
 * xoá bên ngoài, không phải của aggregate. Ghi thẳng bằng SQL nên không tăng {@code version} và
 * không đụng nhau với job hoàn tất xoá đang khoá cùng dòng để đổi trạng thái.
 */
@Component
class IdentityProviderRemovalsAdapter implements IdentityProviderRemovalsPort {

  private final JdbcClient jdbc;

  IdentityProviderRemovalsAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Đi theo partial index {@code ix_users_pending_idp_removal}, gần như rỗng. */
  @Override
  public List<PendingRemoval> pending(int limit) {
    return jdbc.sql(
            """
            SELECT id, external_subject, deletion_requested_at
            FROM users
            WHERE idp_removed_at IS NULL AND status IN ('DELETING', 'DELETED')
            ORDER BY deletion_requested_at
            LIMIT :limit
            """)
        .param("limit", limit)
        .query(
            (rs, row) ->
                new PendingRemoval(
                    new UserId(rs.getObject("id", UUID.class)),
                    ExternalSubject.of(rs.getString("external_subject")),
                    rs.getObject("deletion_requested_at", OffsetDateTime.class).toInstant()))
        .list();
  }

  @Override
  public void markRemoved(UserId userId, Instant removedAt) {
    jdbc.sql(
            """
            UPDATE users SET idp_removed_at = :removedAt
            WHERE id = :id AND idp_removed_at IS NULL AND status IN ('DELETING', 'DELETED')
            """)
        .param("removedAt", removedAt.atOffset(ZoneOffset.UTC))
        .param("id", userId.value())
        .update();
  }
}
