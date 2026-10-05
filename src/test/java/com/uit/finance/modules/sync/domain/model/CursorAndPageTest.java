package com.uit.finance.modules.sync.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.finance.modules.sync.domain.exception.CursorAheadException;
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
