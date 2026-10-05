package com.uit.finance.modules.sync.adapter.out.persistence;

import com.uit.finance.modules.sync.application.port.out.OpLogPort;
import com.uit.finance.modules.sync.domain.model.OpLogEntry;
import com.uit.finance.modules.sync.domain.model.OpOutcome;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Bảng {@code sync_ops} (V6). */
@Component
class JdbcOpLog implements OpLogPort {

  private final JdbcClient jdbc;

  JdbcOpLog(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<OpOutcome> findOutcome(UserId owner, UUID opId) {
    return jdbc.sql("SELECT outcome FROM sync_ops WHERE user_id = :userId AND op_id = :opId")
        .param("userId", owner.value())
        .param("opId", opId)
        .query(String.class)
        .optional()
        .map(OpOutcome::valueOf);
  }

  /**
   * Dòng đã có thì chỉ ghi đè khi đang {@code RETRY}. Dòng đang được một transaction khác ghi thì
   * Postgres chờ transaction đó xong rồi mới quyết định, nên 2 request cùng {@code opId} không thể
   * cùng thắng.
   */
  @Override
  public boolean record(UserId owner, OpLogEntry entry) {
    return jdbc.sql(
                """
                INSERT INTO sync_ops (user_id, op_id, device_id, entity, entity_id, action,
                                      outcome, code, processed_at)
                VALUES (:userId, :opId, :deviceId, :entity, :entityId, :action,
                        :outcome, :code, :processedAt)
                ON CONFLICT (user_id, op_id) DO UPDATE
                  SET device_id = EXCLUDED.device_id, outcome = EXCLUDED.outcome,
                      code = EXCLUDED.code, processed_at = EXCLUDED.processed_at
                  WHERE sync_ops.outcome = 'RETRY'
                """)
            .param("userId", owner.value())
            .param("opId", entry.opId())
            .param("deviceId", entry.deviceId().value())
            .param("entity", entry.entity())
            .param("entityId", entry.entityId())
            .param("action", entry.action().name())
            .param("outcome", entry.outcome().name())
            .param("code", entry.code(), java.sql.Types.VARCHAR)
            .param("processedAt", entry.processedAt().atOffset(ZoneOffset.UTC))
            .update()
        == 1;
  }

  /** {@code ctid} + {@code LIMIT}: Postgres không có {@code DELETE ... LIMIT}. */
  @Override
  public int purgeProcessedBefore(Instant cutoff, int batchSize) {
    return jdbc.sql(
            """
            DELETE FROM sync_ops
            WHERE ctid IN (SELECT ctid FROM sync_ops WHERE processed_at < :cutoff LIMIT :batchSize)
            """)
        .param("cutoff", cutoff.atOffset(ZoneOffset.UTC))
        .param("batchSize", batchSize)
        .update();
  }
}
