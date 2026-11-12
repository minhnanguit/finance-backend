package com.uit.finance.modules.ledger.adapter.out.persistence.maintenance;

import com.uit.finance.modules.ledger.adapter.out.persistence.support.SqlValues;
import com.uit.finance.modules.ledger.application.port.out.maintenance.PurgeLedgerPort;
import com.uit.finance.modules.ledger.application.port.out.maintenance.TombstonePort;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Xoá thật dữ liệu ledger: cả sổ của tài khoản đã xoá, và tombstone quá hạn (ADR-008 A7, T1). SQL
 * viết tay vì cần {@code DELETE ... RETURNING} và {@code NOT EXISTS}; mọi câu đều có {@code
 * user_id}.
 *
 * <p>Tombstone chỉ bị xoá khi không còn dòng nào trỏ tới (FK ghép trong V4 vẫn áp cho tombstone):
 * ví đã xoá còn giao dịch đã xoá trỏ vào thì đợi giao dịch đó tới hạn trước, lần dọn sau mới lấy
 * ví.
 */
@Component
class LedgerMaintenanceAdapter implements PurgeLedgerPort, TombstonePort {

  private final NamedParameterJdbcTemplate jdbc;

  LedgerMaintenanceAdapter(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ---- PurgeLedgerPort ----

  /**
   * Mỗi bảng một câu: FK {@code NO ACTION} được kiểm ở cuối câu lệnh, nên danh mục cha và con của
   * cùng user xoá chung một câu không vướng nhau.
   */
  @Override
  public long deleteEverythingOf(UserId owner) {
    var params = new SqlValues().uuid("owner", owner.value()).build();
    long deleted = 0;
    deleted += jdbc.update("DELETE FROM transactions WHERE user_id = :owner", params);
    deleted += jdbc.update("DELETE FROM categories WHERE user_id = :owner", params);
    deleted += jdbc.update("DELETE FROM accounts WHERE user_id = :owner", params);
    return deleted;
  }

  // ---- TombstonePort ----

  /** Đi theo partial index {@code ix_<bảng>_tombstones (user_id, deleted_at)} của V10. */
  @Override
  public List<UserId> ownersWithTombstonesBefore(
      LedgerEntity entity, Instant cutoff, @Nullable UserId afterOwner, int limit) {
    String table = tableOf(entity);
    String afterClause = afterOwner == null ? "" : " AND user_id > :after";
    SqlValues params = new SqlValues().timestamp("cutoff", cutoff).number("limit", limit);
    if (afterOwner != null) {
      params.uuid("after", afterOwner.value());
    }
    return jdbc
        .queryForList(
            "SELECT DISTINCT user_id FROM "
                + table
                + " WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff"
                + afterClause
                + " ORDER BY user_id LIMIT :limit",
            params.build(),
            UUID.class)
        .stream()
        .map(UserId::new)
        .toList();
  }

  @Override
  public OptionalLong purgeTombstones(LedgerEntity entity, UserId owner, Instant cutoff) {
    String delete =
        switch (entity) {
          case TRANSACTION ->
              """
              DELETE FROM transactions t
              WHERE t.user_id = :owner AND t.deleted_at < :cutoff
              RETURNING t.change_seq
              """;
          case CATEGORY ->
              """
              DELETE FROM categories c
              WHERE c.user_id = :owner AND c.deleted_at < :cutoff
                AND NOT EXISTS (SELECT 1 FROM transactions t
                                WHERE t.user_id = c.user_id AND t.category_id = c.id)
                AND NOT EXISTS (SELECT 1 FROM categories k
                                WHERE k.user_id = c.user_id AND k.parent_id = c.id)
              RETURNING c.change_seq
              """;
          case ACCOUNT ->
              """
              DELETE FROM accounts a
              WHERE a.user_id = :owner AND a.deleted_at < :cutoff
                AND NOT EXISTS (SELECT 1 FROM transactions t
                                WHERE t.user_id = a.user_id AND t.account_id = a.id)
                AND NOT EXISTS (SELECT 1 FROM transactions t
                                WHERE t.user_id = a.user_id AND t.counter_account_id = a.id)
              RETURNING a.change_seq
              """;
        };
    Long maxSeq =
        jdbc.queryForObject(
            "WITH purged AS (" + delete + ") SELECT max(change_seq) FROM purged",
            new SqlValues().uuid("owner", owner.value()).timestamp("cutoff", cutoff).build(),
            Long.class);
    return maxSeq == null ? OptionalLong.empty() : OptionalLong.of(maxSeq);
  }

  private static String tableOf(LedgerEntity entity) {
    return switch (entity) {
      case ACCOUNT -> "accounts";
      case CATEGORY -> "categories";
      case TRANSACTION -> "transactions";
    };
  }
}
