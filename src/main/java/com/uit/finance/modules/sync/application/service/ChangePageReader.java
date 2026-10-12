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

  ChangePage read(UserId owner, Cursor since, int limit) {
    since.requireNotAhead(state.lastIssuedSeq(owner));
    return ChangePage.of(handlers.changesSince(owner, since.afterSeq(), limit + 1), limit, since);
  }
}
