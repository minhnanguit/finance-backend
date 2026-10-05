package com.uit.finance.modules.sync.domain.model;

import java.util.Comparator;
import java.util.List;

/** Một trang pull (ADR-002 §4). */
public record ChangePage(List<ChangeRecord> changes, Cursor next, boolean hasMore) {

  public ChangePage {
    changes = List.copyOf(changes);
  }

  /**
   * Cắt trang từ các thay đổi đã đọc.
   *
   * @param candidates đọc với giới hạn {@code limit + 1}: có phần tử thứ {@code limit + 1} nghĩa là
   *     còn trang sau, không phải đếm thêm
   */
  public static ChangePage of(List<ChangeRecord> candidates, int limit, Cursor since) {
    List<ChangeRecord> sorted =
        candidates.stream().sorted(Comparator.comparingLong(ChangeRecord::changeSeq)).toList();
    boolean hasMore = sorted.size() > limit;
    List<ChangeRecord> page = hasMore ? sorted.subList(0, limit) : sorted;
    Cursor next = page.isEmpty() ? since : new Cursor(page.getLast().changeSeq());
    return new ChangePage(page, next, hasMore);
  }
}
