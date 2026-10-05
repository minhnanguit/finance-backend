package com.uit.finance.modules.sync.adapter.out.persistence;

import com.uit.finance.shared.kernel.UserId;
import com.uit.finance.shared.sync.ChangeSequencer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link ChangeSequencer} trên bảng {@code user_sync_state}: một câu upsert vừa tạo dòng ở lần đầu,
 * vừa khoá dòng và tăng số. Hai request ghi của cùng user xếp hàng ở đây tới khi request trước
 * commit; user khác nhau không chờ nhau.
 *
 * <p>Implement SPI của {@code shared} trực tiếp, giống {@code JdbcProcessedEventStore}: một câu SQL
 * hạ tầng, không có luật nghiệp vụ để đặt vào application.
 */
@Component
class JdbcChangeSequencer implements ChangeSequencer {

  private final JdbcClient jdbc;

  JdbcChangeSequencer(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

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
    // DO UPDATE không đổi gì vẫn giữ row lock tới hết transaction.
    jdbc.sql(
            """
            INSERT INTO user_sync_state (user_id, last_seq, updated_at)
            VALUES (:userId, 0, now())
            ON CONFLICT (user_id) DO UPDATE SET last_seq = user_sync_state.last_seq
            RETURNING last_seq
            """)
        .param("userId", owner.value())
        .query(Long.class)
        .single();
  }

  /** Ngoài transaction thì lock nhả ngay sau câu lệnh và thứ tự số không còn khớp thứ tự commit. */
  private static void requireTransaction() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "change_seq must be allocated inside the writing transaction");
    }
  }
}
