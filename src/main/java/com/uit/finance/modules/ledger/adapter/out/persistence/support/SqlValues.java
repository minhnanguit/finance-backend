package com.uit.finance.modules.ledger.adapter.out.persistence.support;

import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Tham số SQL có khai báo kiểu, để giá trị {@code null} vẫn bind đúng kiểu cột (uuid, timestamptz).
 * Không khai báo kiểu thì Postgres có thể đoán nhầm thành {@code bytea}.
 */
public final class SqlValues {

  private final MapSqlParameterSource params = new MapSqlParameterSource();

  public SqlValues uuid(String name, @Nullable UUID value) {
    params.addValue(name, value, Types.OTHER);
    return this;
  }

  public SqlValues text(String name, @Nullable String value) {
    params.addValue(name, value, Types.VARCHAR);
    return this;
  }

  public SqlValues number(String name, long value) {
    params.addValue(name, value, Types.BIGINT);
    return this;
  }

  public SqlValues date(String name, @Nullable LocalDate value) {
    params.addValue(name, value, Types.DATE);
    return this;
  }

  /** {@link Instant} đi qua {@code OffsetDateTime} UTC vì JDBC driver không bind thẳng Instant. */
  public SqlValues timestamp(String name, @Nullable Instant value) {
    params.addValue(
        name, value == null ? null : value.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
    return this;
  }

  public MapSqlParameterSource build() {
    return params;
  }
}
