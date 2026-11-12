package com.uit.finance.modules.sync.application.service;

import com.uit.finance.modules.sync.application.port.out.SyncHandlersPort;
import com.uit.finance.modules.sync.application.port.out.SyncStatePort;
import com.uit.finance.modules.sync.domain.model.ChangePage;
import com.uit.finance.modules.sync.domain.model.Cursor;
import com.uit.finance.shared.kernel.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đọc một trang trong **một snapshot** ({@code REPEATABLE READ}).
 *
 * <p>Mỗi entity là một câu query riêng. Ở {@code READ COMMITTED}, một lần ghi commit giữa 2 câu có
 * thể bị câu đầu bỏ lỡ trong khi câu sau trả số lớn hơn nó, và cursor sẽ nhảy qua mất. Trong một
 * snapshot, các số nhìn thấy luôn liền từ 1: số phát dưới row lock nên lần ghi số nhỏ luôn commit
 * trước lần ghi số lớn (ADR-002 §2).
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
class ChangePageReader {

  private final SyncStatePort state;
  private final SyncHandlersPort handlers;

  ChangePageReader(SyncStatePort state, SyncHandlersPort handlers) {
    this.state = state;
    this.handlers = handlers;
  }

  /**
   * Mốc dọn tombstone đọc trong cùng snapshot với các trang: job dọn xoá tombstone và nâng mốc
   * trong một transaction, nên hoặc snapshot còn thấy tombstone, hoặc thấy mốc mới và trả {@code
   * 410}. Không có lúc client lỡ một lần xoá mà vẫn đi tiếp (ADR-008 T3).
   */
  ChangePage read(UserId owner, Cursor since, int limit) {
    since.requireNotAhead(state.lastIssuedSeq(owner));
    long purgedThrough = state.purgedThroughSeq(owner);
    Cursor from = since.isStart() ? Cursor.startingAt(purgedThrough) : since;
    from.requireNotExpired(purgedThrough);
    return ChangePage.of(handlers.changesSince(owner, from.afterSeq(), limit + 1), limit, from);
  }
}
