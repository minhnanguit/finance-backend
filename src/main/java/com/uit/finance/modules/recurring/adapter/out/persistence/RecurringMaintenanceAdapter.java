package com.uit.finance.modules.recurring.adapter.out.persistence;

import com.uit.finance.modules.recurring.application.port.out.PurgeRecurringsPort;
import com.uit.finance.modules.recurring.application.port.out.RecurringTombstonePort;
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
 * Xoá thật dòng {@code recurrings} (ADR-008): tombstone quá hạn (T1, đi theo partial index {@code
 * ix_recurrings_tombstones}) và toàn bộ khoản định kỳ của tài khoản đã xoá (A7). Timeline ({@code
 * recurring_revisions}) đi theo {@code ON DELETE CASCADE}; bảng ngoài module không trỏ tới đây.
 */
@Component
class RecurringMaintenanceAdapter implements RecurringTombstonePort, PurgeRecurringsPort {

  private final NamedParameterJdbcTemplate jdbc;

  RecurringMaintenanceAdapter(NamedParameterJdbcTemplate jdbc) {
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
            "SELECT DISTINCT user_id FROM recurrings"
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
              DELETE FROM recurrings
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

  @Override
  public long deleteEverythingOf(UserId owner) {
    return jdbc.update(
        "DELETE FROM recurrings WHERE user_id = :owner",
        new MapSqlParameterSource().addValue("owner", owner.value(), Types.OTHER));
  }

  private static OffsetDateTime utc(Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }
}
