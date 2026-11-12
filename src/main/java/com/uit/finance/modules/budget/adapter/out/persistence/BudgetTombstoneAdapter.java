package com.uit.finance.modules.budget.adapter.out.persistence;

import com.uit.finance.modules.budget.application.port.out.BudgetTombstonePort;
import com.uit.finance.shared.kernel.UserId;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Xoá thật tombstone ngân sách quá hạn (ADR-008 T1). Đi theo partial index {@code
 * ix_budgets_tombstones}. Không bảng nào tham chiếu {@code budgets}, nên xoá không cần {@code NOT
 * EXISTS}.
 */
@Component
class BudgetTombstoneAdapter implements BudgetTombstonePort {

  private final NamedParameterJdbcTemplate jdbc;

  BudgetTombstoneAdapter(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<UserId> ownersWithTombstonesBefore(
      Instant cutoff, @Nullable UserId afterOwner, int limit) {
    String afterClause = afterOwner == null ? "" : " AND user_id > :after";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("cutoff", utc(cutoff), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("limit", limit, Types.INTEGER);
    if (afterOwner != null) {
      params.addValue("after", afterOwner.value(), Types.OTHER);
    }
    return jdbc
        .queryForList(
            "SELECT DISTINCT user_id FROM budgets"
                + " WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff"
                + afterClause
                + " ORDER BY user_id LIMIT :limit",
            params,
            UUID.class)
        .stream()
        .map(UserId::new)
        .toList();
  }

  @Override
  public OptionalLong purgeTombstones(UserId owner, Instant cutoff) {
    Long maxSeq =
        jdbc.queryForObject(
            """
            WITH purged AS (
              DELETE FROM budgets
              WHERE user_id = :owner AND deleted_at < :cutoff
              RETURNING change_seq
            )
            SELECT max(change_seq) FROM purged
            """,
            new MapSqlParameterSource()
                .addValue("owner", owner.value(), Types.OTHER)
                .addValue("cutoff", utc(cutoff), Types.TIMESTAMP_WITH_TIMEZONE),
            Long.class);
    return maxSeq == null ? OptionalLong.empty() : OptionalLong.of(maxSeq);
  }

  private static OffsetDateTime utc(Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }
}
