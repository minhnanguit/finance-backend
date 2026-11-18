package com.uit.finance.modules.sync.domain.model;

import com.uit.finance.modules.sync.domain.exception.CursorAheadException;
import com.uit.finance.modules.sync.domain.exception.CursorExpiredException;
import com.uit.finance.modules.sync.domain.exception.InvalidCursorException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Dấu trang của pull (ADR-002 §2). Gửi cho client dưới dạng chuỗi opaque để đổi cách mã hoá mà
 * client không phải sửa: {@code "<afterSeq>"}, hoặc {@code "<afterSeq>.<floor>"} khi đang pull lại
 * từ đầu sau một lần dọn tombstone (ADR-008 T3).
 *
 * @param afterSeq {@code change_seq} cuối cùng client đã nhận
 * @param floor mốc dọn tombstone tại lúc client bắt đầu pull từ đầu: tombstone đã dọn trước mốc này
 *     không liên quan tới client, vì lúc đó client chưa giữ gì. 0 khi không cần (client đã vượt
 *     mốc)
 */
public record Cursor(long afterSeq, long floor) {

  public static final Cursor START = new Cursor(0, 0);

  private static final Pattern FORMAT = Pattern.compile("([0-9]{1,18})(?:\\.([0-9]{1,18}))?");

  public Cursor {
    if (afterSeq < 0 || floor < 0) {
      throw new InvalidCursorException();
    }
  }

  /** Cursor thường, không có mốc. */
  public Cursor(long afterSeq) {
    this(afterSeq, 0);
  }

  /** Rỗng = từ đầu. */
  public static Cursor parse(@Nullable String raw) {
    if (raw == null || raw.isBlank()) {
      return START;
    }
    Matcher matcher = FORMAT.matcher(raw);
    if (!matcher.matches()) {
      throw new InvalidCursorException();
    }
    long floor = matcher.group(2) == null ? 0 : Long.parseLong(matcher.group(2));
    return new Cursor(Long.parseLong(matcher.group(1)), floor);
  }

  /** Client chưa giữ gì: pull toàn bộ. */
  public boolean isStart() {
    return afterSeq == 0 && floor == 0;
  }

  /**
   * Bắt đầu pull từ đầu khi server đã dọn tombstone tới {@code purgedThroughSeq}: ghi mốc vào
   * cursor để các trang sau (có {@code afterSeq} còn thấp hơn mốc) không bị coi là cũ.
   */
  public static Cursor startingAt(long purgedThroughSeq) {
    return new Cursor(0, purgedThroughSeq);
  }

  /** Cursor lớn hơn số cuối server đã phát thì server đã mất dữ liệu client đang giữ. */
  public void requireNotAhead(long lastIssuedSeq) {
    if (afterSeq > lastIssuedSeq) {
      throw new CursorAheadException();
    }
  }

  /**
   * Server đã xoá thật tombstone tới {@code purgedThroughSeq}. Client đã nhận tới {@code afterSeq}
   * (hoặc bắt đầu sau mốc {@code floor}) mà vẫn thấp hơn mốc thì có thể đã lỡ một lần xoá: phải
   * pull lại từ đầu.
   */
  public void requireNotExpired(long purgedThroughSeq) {
    if (purgedThroughSeq > Math.max(afterSeq, floor)) {
      throw new CursorExpiredException();
    }
  }

  /** Cursor sau khi nhận tới {@code seq}. Đã vượt mốc thì bỏ mốc cho gọn. */
  public Cursor advancedTo(long seq) {
    return new Cursor(seq, seq >= floor ? 0 : floor);
  }

  public String encode() {
    return floor == 0 ? Long.toString(afterSeq) : afterSeq + "." + floor;
  }
}
