package com.uit.finance.modules.sync.adapter.out.persistence;

import com.uit.finance.modules.sync.application.port.out.SyncStatePort;
import com.uit.finance.shared.kernel.UserId;
import com.uit.finance.shared.sync.ChangeSequencer;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Mọi SQL trên bảng {@code user_sync_state}, mỗi user một dòng (ADR-002 §2, §5).
 *
 * <p>Hai vai trên cùng một dòng và cùng một row lock:
 *
 * <ul>
 *   <li>{@link ChangeSequencer} cho module ghi dữ liệu: một câu upsert vừa tạo dòng ở lần đầu, vừa
 *       khoá dòng, vừa tăng số. Hai lần ghi của cùng user xếp hàng ở đây tới khi lần trước commit;
 *       user khác nhau không chờ nhau.
 *   <li>{@link SyncStatePort} cho chính module sync: số cuối đã phát, trạng thái khởi tạo.
 * </ul>
 */
@Component
class JdbcUserSyncState implements ChangeSequencer, SyncStatePort {

  /** Tạo dòng nếu chưa có và giữ row lock tới hết transaction, không đổi số. */
  private static final String LOCK_ROW =
      """
      INSERT INTO user_sync_state (user_id, last_seq, updated_at)
      VALUES (:userId, 0, now())
      ON CONFLICT (user_id) DO UPDATE SET last_seq = user_sync_state.last_seq
      """;

  private final JdbcClient jdbc;

  JdbcUserSyncState(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  // ---- ChangeSequencer ----

  @Override
  public long reserve(UserId owner, int count) {
    if (count < 1) {
      throw new IllegalArgumentException("count must be positive");
    }
    requireTransaction();
    long last =
        jdbc.sql(
                """
                INSERT INTO user_sync_state (user_id, last_seq, updated_at)
                VALUES (:userId, :count, now())
                ON CONFLICT (user_id)
                DO UPDATE SET last_seq = user_sync_state.last_seq + :count, updated_at = now()
                RETURNING last_seq
                """)
            .param("userId", owner.value())
            .param("count", count)
            .query(Long.class)
            .single();
    return last - count + 1;
  }

  @Override
  public void lock(UserId owner) {
    requireTransaction();
    jdbc.sql(LOCK_ROW).param("userId", owner.value()).update();
  }

  // ---- SyncStatePort ----

  @Override
  public boolean isInitialized(UserId owner) {
    return jdbc.sql(
            "SELECT initialized_at IS NOT NULL FROM user_sync_state WHERE user_id = :userId")
        .param("userId", owner.value())
        .query(Boolean.class)
        .optional()
        .orElse(false);
  }

  @Override
  public boolean lockForInitialization(UserId owner) {
    requireTransaction();
    // RETURNING đọc dòng sau khi đã giữ khoá: nếu request khác vừa khởi tạo xong thì thấy ngay.
    return jdbc.sql(LOCK_ROW + " RETURNING initialized_at IS NULL")
        .param("userId", owner.value())
        .query(Boolean.class)
        .single();
  }

  @Override
  public void markInitialized(UserId owner, Instant at) {
    jdbc.sql(
            """
            UPDATE user_sync_state SET initialized_at = :at, updated_at = now()
            WHERE user_id = :userId
            """)
        .param("userId", owner.value())
        .param("at", at.atOffset(ZoneOffset.UTC))
        .update();
  }

  @Override
  public long lastIssuedSeq(UserId owner) {
    return jdbc.sql("SELECT last_seq FROM user_sync_state WHERE user_id = :userId")
        .param("userId", owner.value())
        .query(Long.class)
        .optional()
        .orElse(0L);
  }

  /** Ngoài transaction thì khoá nhả ngay sau câu lệnh và thứ tự số không còn khớp thứ tự commit. */
  private static void requireTransaction() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "user_sync_state must be locked inside the writing transaction");
    }
  }
}
