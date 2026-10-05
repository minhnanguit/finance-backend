package com.uit.finance.shared.sync;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Đọc chặt {@code data} của một op: chỉ nhận đúng các field khai báo, đúng kiểu JSON (ADR-006 B3).
 *
 * <p>Field lạ, kể cả field do server quản lý ({@code userId}, {@code changeSeq}, {@code
 * version}...), làm op bị {@code REJECTED sync.unknown_field}. Sai kiểu (số thập phân cho số tiền,
 * chuỗi cho boolean...) là {@code sync.invalid_field}. Không dùng Jackson ở đây để không có ép kiểu
 * ngầm, ví dụ 1.9 bị cắt thành 1.
 *
 * <p>Thiếu field hay {@code null} trả {@code null}; luật bắt buộc nằm ở domain, nơi có mã lỗi
 * riêng.
 */
public final class SyncData {

  private final Map<String, Object> values;

  private SyncData(Map<String, Object> values) {
    this.values = values;
  }

  /**
   * @throws SyncDataException {@code sync.invalid_field} khi thiếu {@code data}, {@code
   *     sync.unknown_field} khi có field ngoài {@code allowed}
   */
  public static SyncData read(@Nullable Map<String, Object> data, Set<String> allowed) {
    if (data == null) {
      throw SyncDataException.invalid("data");
    }
    for (String key : data.keySet()) {
      if (!allowed.contains(key)) {
        throw SyncDataException.unknown(key);
      }
    }
    return new SyncData(data);
  }

  public @Nullable String text(String field) {
    Object value = raw(field);
    if (value == null) {
      return null;
    }
    if (value instanceof String text) {
      return text;
    }
    throw SyncDataException.invalid(field);
  }

  public @Nullable UUID uuid(String field) {
    String text = text(field);
    if (text == null) {
      return null;
    }
    try {
      return UUID.fromString(text);
    } catch (IllegalArgumentException e) {
      throw SyncDataException.invalid(field);
    }
  }

  /** Số nguyên bắt buộc. Số thập phân bị từ chối chứ không bị cắt. */
  public long requiredLong(String field) {
    Object value = raw(field);
    if (value instanceof Integer || value instanceof Long || value instanceof Short) {
      return ((Number) value).longValue();
    }
    if (value instanceof BigInteger big && big.bitLength() < Long.SIZE) {
      return big.longValue();
    }
    throw SyncDataException.invalid(field);
  }

  public int requiredInt(String field) {
    long value = requiredLong(field);
    if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
      throw SyncDataException.invalid(field);
    }
    return (int) value;
  }

  public boolean requiredBoolean(String field) {
    if (raw(field) instanceof Boolean flag) {
      return flag;
    }
    throw SyncDataException.invalid(field);
  }

  /** Ngày dạng ISO {@code 2026-10-05}. */
  public @Nullable LocalDate date(String field) {
    String text = text(field);
    if (text == null) {
      return null;
    }
    try {
      return LocalDate.parse(text);
    } catch (DateTimeParseException e) {
      throw SyncDataException.invalid(field);
    }
  }

  /** Thời điểm ISO-8601 có offset, ví dụ {@code 2026-10-05T08:30:00+07:00}. */
  public @Nullable Instant instant(String field) {
    String text = text(field);
    if (text == null) {
      return null;
    }
    try {
      return OffsetDateTime.parse(text).toInstant();
    } catch (DateTimeParseException e) {
      throw SyncDataException.invalid(field);
    }
  }

  private @Nullable Object raw(String field) {
    return values.get(field);
  }
}
