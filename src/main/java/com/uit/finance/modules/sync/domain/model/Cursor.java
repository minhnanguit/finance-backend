package com.uit.finance.modules.sync.domain.model;

import com.uit.finance.modules.sync.domain.exception.CursorAheadException;
import com.uit.finance.modules.sync.domain.exception.InvalidCursorException;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Dấu trang của pull: {@code change_seq} cuối cùng client đã nhận (ADR-002 §2). Gửi cho client dưới
 * dạng chuỗi opaque để sau này đổi cách mã hoá mà client không phải sửa.
 */
public record Cursor(long afterSeq) {

  public static final Cursor START = new Cursor(0);

  private static final Pattern DIGITS = Pattern.compile("[0-9]{1,18}");

  public Cursor {
    if (afterSeq < 0) {
      throw new InvalidCursorException();
    }
  }

  /** Rỗng = từ đầu. */
  public static Cursor parse(@Nullable String raw) {
    if (raw == null || raw.isBlank()) {
      return START;
    }
    if (!DIGITS.matcher(raw).matches()) {
      throw new InvalidCursorException();
    }
    return new Cursor(Long.parseLong(raw));
  }

  /** Cursor lớn hơn số cuối server đã phát thì server đã mất dữ liệu client đang giữ. */
  public void requireNotAhead(long lastIssuedSeq) {
    if (afterSeq > lastIssuedSeq) {
      throw new CursorAheadException();
    }
  }

  public String encode() {
    return Long.toString(afterSeq);
  }
}
