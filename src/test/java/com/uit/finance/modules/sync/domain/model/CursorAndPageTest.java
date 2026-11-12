package com.uit.finance.modules.sync.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.finance.modules.sync.domain.exception.CursorAheadException;
import com.uit.finance.modules.sync.domain.exception.CursorExpiredException;
import com.uit.finance.modules.sync.domain.exception.InvalidCursorException;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CursorAndPageTest {

  private static List<ChangeRecord> changes(long... seqs) {
    return LongStream.of(seqs)
        .mapToObj(seq -> new ChangeRecord("account", UUID.randomUUID(), seq, false, null))
        .toList();
  }

  @Test
  @DisplayName("cursor rỗng là từ đầu; chỉ nhận số không âm; vượt server là cursor_ahead")
  void cursor() {
    assertThat(Cursor.parse(null)).isEqualTo(Cursor.START);
    assertThat(Cursor.parse(" ")).isEqualTo(Cursor.START);
    assertThat(Cursor.parse("42").afterSeq()).isEqualTo(42);
    assertThat(Cursor.parse("42").encode()).isEqualTo("42");

    assertThatThrownBy(() -> Cursor.parse("-1")).isInstanceOf(InvalidCursorException.class);
    assertThatThrownBy(() -> Cursor.parse("abc")).isInstanceOf(InvalidCursorException.class);
    assertThatThrownBy(() -> Cursor.parse("9".repeat(19)))
        .isInstanceOf(InvalidCursorException.class);
    assertThatThrownBy(() -> Cursor.parse("43").requireNotAhead(42))
        .isInstanceOf(CursorAheadException.class);
  }

  @Test
  @DisplayName("cursor có mốc dọn: mã hoá '<seq>.<mốc>', vượt mốc thì bỏ mốc")
  void cursorWithPurgeFloor() {
    Cursor resuming = Cursor.parse("2.900");

    assertThat(resuming).isEqualTo(new Cursor(2, 900));
    assertThat(resuming.isStart()).isFalse();
    assertThat(resuming.encode()).isEqualTo("2.900");
    assertThat(resuming.advancedTo(500).encode()).isEqualTo("500.900");
    assertThat(resuming.advancedTo(900).encode()).isEqualTo("900");
    assertThat(Cursor.startingAt(900)).isEqualTo(new Cursor(0, 900));
    assertThat(Cursor.startingAt(0).isStart()).isTrue();

    assertThatThrownBy(() -> Cursor.parse("2.")).isInstanceOf(InvalidCursorException.class);
    assertThatThrownBy(() -> Cursor.parse(".2")).isInstanceOf(InvalidCursorException.class);
    assertThatThrownBy(() -> Cursor.parse("1.2.3")).isInstanceOf(InvalidCursorException.class);
  }

  @Test
  @DisplayName("cursor_expired khi server đã dọn tombstone cao hơn cả cursor lẫn mốc của nó")
  void cursorExpiry() {
    // Máy cũ: đã nhận tới 100, server dọn tombstone tới 150 → có thể đã lỡ một lần xoá.
    assertThatThrownBy(() -> new Cursor(100).requireNotExpired(150))
        .isInstanceOf(CursorExpiredException.class);
    // Đã nhận vượt mốc: không lỡ gì.
    new Cursor(150).requireNotExpired(150);
    new Cursor(200).requireNotExpired(150);
    // Đang pull lại từ đầu sau lần dọn ở mốc 150: trang giữa chừng (afterSeq 20) vẫn hợp lệ.
    new Cursor(20, 150).requireNotExpired(150);
    // Nhưng server dọn thêm tới 180 trong lúc đó thì phải bắt đầu lại.
    assertThatThrownBy(() -> new Cursor(20, 150).requireNotExpired(180))
        .isInstanceOf(CursorExpiredException.class);
  }

  @Test
  @DisplayName("trang của lần pull lại từ đầu mang mốc theo, kể cả trang rỗng")
  void pagesCarryTheFloor() {
    ChangePage first = ChangePage.of(changes(1, 2, 3), 2, Cursor.startingAt(50));
    ChangePage empty = ChangePage.of(List.of(), 2, Cursor.startingAt(50));

    assertThat(first.next()).isEqualTo(new Cursor(2, 50));
    assertThat(empty.next().encode()).isEqualTo("0.50");
  }

  @Test
  @DisplayName("đọc limit + 1: có phần tử thừa nghĩa là còn trang sau; cursor = số cuối của trang")
  void pageCutsAtLimit() {
    ChangePage page = ChangePage.of(changes(3, 1, 2), 2, Cursor.START);

    assertThat(page.changes()).extracting(ChangeRecord::changeSeq).containsExactly(1L, 2L);
    assertThat(page.hasMore()).isTrue();
    assertThat(page.next().afterSeq()).isEqualTo(2);
  }

  @Test
  @DisplayName("trang cuối: không còn gì; trang rỗng giữ nguyên cursor cũ")
  void lastAndEmptyPages() {
    ChangePage last = ChangePage.of(changes(5), 2, new Cursor(4));
    ChangePage empty = ChangePage.of(List.of(), 2, new Cursor(9));

    assertThat(last.hasMore()).isFalse();
    assertThat(last.next().afterSeq()).isEqualTo(5);
    assertThat(empty.hasMore()).isFalse();
    assertThat(empty.next().afterSeq()).isEqualTo(9);
  }
}
