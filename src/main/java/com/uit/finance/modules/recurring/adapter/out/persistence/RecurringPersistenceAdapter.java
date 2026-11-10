package com.uit.finance.modules.recurring.adapter.out.persistence;

import com.uit.finance.modules.recurring.application.port.out.LoadRecurringChangesPort;
import com.uit.finance.modules.recurring.application.port.out.LoadRecurringPort;
import com.uit.finance.modules.recurring.application.port.out.RecurringCountPort;
import com.uit.finance.modules.recurring.application.port.out.SaveRecurringPort;
import com.uit.finance.modules.recurring.application.port.out.Sequenced;
import com.uit.finance.modules.recurring.domain.exception.RecurringNotFoundException;
import com.uit.finance.modules.recurring.domain.model.Frequency;
import com.uit.finance.modules.recurring.domain.model.RecurrenceRevision;
import com.uit.finance.modules.recurring.domain.model.RecurrenceRule;
import com.uit.finance.modules.recurring.domain.model.Recurring;
import com.uit.finance.modules.recurring.domain.model.RecurringId;
import com.uit.finance.modules.recurring.domain.model.RecurringTerms;
import com.uit.finance.modules.recurring.domain.model.RecurringType;
import com.uit.finance.shared.kernel.UserId;
import com.uit.finance.shared.sync.ChangeSequencer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Đọc/ghi khoản định kỳ + timeline bằng JDBC. Mọi query lọc {@code user_id = :owner} (ADR-006 B1);
 * mỗi lần ghi lấy một {@code change_seq} mới trong cùng transaction (ADR-002 §2). Timeline được ghi
 * lại toàn bộ mỗi lần sửa: tối đa 50 dòng, luôn nằm trong transaction đã khoá user.
 */
@Component
class RecurringPersistenceAdapter
    implements LoadRecurringPort, SaveRecurringPort, RecurringCountPort, LoadRecurringChangesPort {

  private static final String COLUMNS =
      """
      id, user_id, name, type, currency, generation_not_before, generation_end_date,
      remind_days_before, revision, change_seq, version, deleted_at
      """;

  private static final String INSERT =
      """
      INSERT INTO recurrings (id, user_id, name, type, currency, generation_not_before,
                              generation_end_date, remind_days_before, revision, change_seq,
                              version, created_at, updated_at, deleted_at)
      VALUES (:id, :userId, :name, :type, :currency, :notBefore, :endDate, :remind, :revision,
              :changeSeq, 0, :now, :now, :deletedAt)
      ON CONFLICT (id) DO NOTHING
      """;

  private static final String UPDATE =
      """
      UPDATE recurrings
         SET name = :name, generation_not_before = :notBefore, generation_end_date = :endDate,
             remind_days_before = :remind, revision = :revision, change_seq = :changeSeq,
             version = version + 1, updated_at = :now, deleted_at = :deletedAt
       WHERE id = :id AND user_id = :userId
      """;

  private static final String INSERT_REVISION =
      """
      INSERT INTO recurring_revisions (id, recurring_id, effective_from, anchor_date, account_id,
                                       category_id, amount_minor, note, frequency, interval_count,
                                       weekday, month, day_of_month, auto_confirm)
      VALUES (:id, :recurringId, :effectiveFrom, :anchorDate, :accountId, :categoryId,
              :amountMinor, :note, :frequency, :interval, :weekday, :month, :dayOfMonth,
              :autoConfirm)
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final ChangeSequencer sequencer;
  private final Clock clock;

  RecurringPersistenceAdapter(
      NamedParameterJdbcTemplate jdbc, ChangeSequencer sequencer, Clock clock) {
    this.jdbc = jdbc;
    this.sequencer = sequencer;
    this.clock = clock;
  }

  @Override
  public Optional<Recurring> find(UserId owner, RecurringId id) {
    return findSequenced(owner, id).map(Sequenced::value);
  }

  @Override
  public Optional<Sequenced<Recurring>> findSequenced(UserId owner, RecurringId id) {
    List<Row> rows =
        jdbc.query(
            "SELECT " + COLUMNS + " FROM recurrings WHERE id = :id AND user_id = :userId",
            new MapSqlParameterSource()
                .addValue("id", id.value(), Types.OTHER)
                .addValue("userId", owner.value(), Types.OTHER),
            RecurringPersistenceAdapter::row);
    return withRevisions(rows).stream().findFirst();
  }

  @Override
  public List<Sequenced<Recurring>> changesSince(UserId owner, long afterSeq, int limit) {
    List<Row> rows =
        jdbc.query(
            "SELECT "
                + COLUMNS
                + " FROM recurrings WHERE user_id = :userId AND change_seq > :afterSeq"
                + " ORDER BY change_seq ASC LIMIT :limit",
            new MapSqlParameterSource()
                .addValue("userId", owner.value(), Types.OTHER)
                .addValue("afterSeq", afterSeq)
                .addValue("limit", limit),
            RecurringPersistenceAdapter::row);
    return withRevisions(rows);
  }

  @Override
  public long countActive(UserId owner, LocalDate endsNotBefore) {
    Long count =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM recurrings
             WHERE user_id = :userId AND deleted_at IS NULL
               AND (generation_end_date IS NULL OR generation_end_date >= :endsNotBefore)
            """,
            new MapSqlParameterSource()
                .addValue("userId", owner.value(), Types.OTHER)
                .addValue("endsNotBefore", endsNotBefore, Types.DATE),
            Long.class);
    return count == null ? 0 : count;
  }

  @Override
  public boolean insertIfAbsent(UserId owner, Recurring recurring) {
    requireOwner(owner, recurring);
    long changeSeq = sequencer.next(owner);
    int inserted = jdbc.update(INSERT, params(recurring, changeSeq));
    if (inserted == 0) {
      return false;
    }
    insertRevisions(recurring);
    return true;
  }

  /**
   * Không cần optimistic lock: mọi lần ghi của user chạy dưới khoá {@code user_sync_state} (RS14),
   * nên đọc-sửa-ghi không thể đan xen. {@code revision} (CAS) đã được domain kiểm.
   */
  @Override
  public void update(UserId owner, Recurring recurring) {
    requireOwner(owner, recurring);
    UUID id = recurring.getId().value();
    long changeSeq = sequencer.next(owner);
    if (jdbc.update(UPDATE, params(recurring, changeSeq)) == 0) {
      throw new RecurringNotFoundException(id);
    }
    jdbc.update(
        "DELETE FROM recurring_revisions WHERE recurring_id = :id",
        new MapSqlParameterSource().addValue("id", id, Types.OTHER));
    insertRevisions(recurring);
  }

  private void insertRevisions(Recurring recurring) {
    UUID recurringId = recurring.getId().value();
    MapSqlParameterSource[] batch =
        recurring.getTerms().revisions().stream()
            .map(r -> revisionParams(recurringId, r))
            .toArray(MapSqlParameterSource[]::new);
    jdbc.batchUpdate(INSERT_REVISION, batch);
  }

  private MapSqlParameterSource params(Recurring recurring, long changeSeq) {
    RecurringTerms terms = recurring.getTerms();
    return new MapSqlParameterSource()
        .addValue("id", recurring.getId().value(), Types.OTHER)
        .addValue("userId", recurring.getOwner().value(), Types.OTHER)
        .addValue("name", terms.name(), Types.VARCHAR)
        .addValue("type", terms.type().name(), Types.VARCHAR)
        .addValue("currency", terms.currency().getCurrencyCode(), Types.VARCHAR)
        .addValue("notBefore", terms.generationNotBefore(), Types.DATE)
        .addValue("endDate", terms.generationEndDate(), Types.DATE)
        .addValue("remind", terms.remindDaysBefore(), Types.SMALLINT)
        .addValue("revision", recurring.getRevision(), Types.BIGINT)
        .addValue("changeSeq", changeSeq, Types.BIGINT)
        .addValue("now", utc(clock.instant()), Types.TIMESTAMP_WITH_TIMEZONE)
        .addValue("deletedAt", utc(recurring.getDeletedAt()), Types.TIMESTAMP_WITH_TIMEZONE);
  }

  private static MapSqlParameterSource revisionParams(UUID recurringId, RecurrenceRevision r) {
    RecurrenceRule rule = r.rule();
    return new MapSqlParameterSource()
        .addValue("id", r.id(), Types.OTHER)
        .addValue("recurringId", recurringId, Types.OTHER)
        .addValue("effectiveFrom", r.effectiveFrom(), Types.DATE)
        .addValue("anchorDate", r.anchorDate(), Types.DATE)
        .addValue("accountId", r.accountId(), Types.OTHER)
        .addValue("categoryId", r.categoryId(), Types.OTHER)
        .addValue("amountMinor", r.amountMinor(), Types.BIGINT)
        .addValue("note", r.note(), Types.VARCHAR)
        .addValue("frequency", rule.frequency().name(), Types.VARCHAR)
        .addValue("interval", rule.interval(), Types.SMALLINT)
        .addValue("weekday", rule.weekday(), Types.SMALLINT)
        .addValue("month", rule.month(), Types.SMALLINT)
        .addValue("dayOfMonth", rule.dayOfMonth(), Types.SMALLINT)
        .addValue("autoConfirm", r.autoConfirm(), Types.BOOLEAN);
  }

  /** Một query cho timeline của cả lô, rồi dựng aggregate theo đúng thứ tự của {@code rows}. */
  private List<Sequenced<Recurring>> withRevisions(List<Row> rows) {
    if (rows.isEmpty()) {
      return List.of();
    }
    Map<UUID, List<RecurrenceRevision>> timelines = new LinkedHashMap<>();
    rows.forEach(row -> timelines.put(row.id(), new ArrayList<>()));
    jdbc.query(
        """
        SELECT id, recurring_id, effective_from, anchor_date, account_id, category_id,
               amount_minor, note, frequency, interval_count, weekday, month, day_of_month,
               auto_confirm
          FROM recurring_revisions
         WHERE recurring_id IN (:ids)
         ORDER BY recurring_id, effective_from
        """,
        new MapSqlParameterSource("ids", new ArrayList<>(timelines.keySet())),
        rs -> {
          timelines.get(rs.getObject("recurring_id", UUID.class)).add(revision(rs));
        });
    List<Sequenced<Recurring>> result = new ArrayList<>(rows.size());
    for (Row row : rows) {
      Recurring recurring =
          Recurring.rehydrate(
              RecurringId.of(row.id()),
              new UserId(row.userId()),
              new RecurringTerms(
                  row.name(),
                  RecurringType.valueOf(row.type()),
                  Currency.getInstance(row.currency()),
                  row.notBefore(),
                  row.endDate(),
                  row.remind(),
                  timelines.get(row.id())),
              row.revision(),
              row.deletedAt());
      result.add(new Sequenced<>(recurring, row.changeSeq()));
    }
    return result;
  }

  private static RecurrenceRevision revision(ResultSet rs) throws SQLException {
    return new RecurrenceRevision(
        rs.getObject("id", UUID.class),
        rs.getObject("effective_from", LocalDate.class),
        rs.getObject("anchor_date", LocalDate.class),
        rs.getObject("account_id", UUID.class),
        rs.getObject("category_id", UUID.class),
        rs.getLong("amount_minor"),
        rs.getString("note"),
        new RecurrenceRule(
            Frequency.valueOf(rs.getString("frequency")),
            rs.getInt("interval_count"),
            nullableInt(rs, "weekday"),
            nullableInt(rs, "month"),
            nullableInt(rs, "day_of_month")),
        rs.getBoolean("auto_confirm"));
  }

  private record Row(
      UUID id,
      UUID userId,
      String name,
      String type,
      String currency,
      LocalDate notBefore,
      @Nullable LocalDate endDate,
      @Nullable Integer remind,
      long revision,
      long changeSeq,
      long version,
      @Nullable Instant deletedAt) {}

  private static Row row(ResultSet rs, int rowNum) throws SQLException {
    Timestamp deleted = rs.getTimestamp("deleted_at");
    return new Row(
        rs.getObject("id", UUID.class),
        rs.getObject("user_id", UUID.class),
        rs.getString("name"),
        rs.getString("type"),
        rs.getString("currency"),
        rs.getObject("generation_not_before", LocalDate.class),
        rs.getObject("generation_end_date", LocalDate.class),
        nullableInt(rs, "remind_days_before"),
        rs.getLong("revision"),
        rs.getLong("change_seq"),
        rs.getLong("version"),
        deleted == null ? null : deleted.toInstant());
  }

  private static @Nullable Integer nullableInt(ResultSet rs, String column) throws SQLException {
    int value = rs.getInt(column);
    return rs.wasNull() ? null : value;
  }

  private static @Nullable OffsetDateTime utc(@Nullable Instant value) {
    return value == null ? null : value.atOffset(ZoneOffset.UTC);
  }

  /** Hợp đồng của port out: {@code owner} truyền vào phải đúng là chủ của bản ghi (B1). */
  private static void requireOwner(UserId owner, Recurring recurring) {
    if (!recurring.isOwnedBy(owner)) {
      throw new IllegalArgumentException("owner does not match the record being written");
    }
  }
}
