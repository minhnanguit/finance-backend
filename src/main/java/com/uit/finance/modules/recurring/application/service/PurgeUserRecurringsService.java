package com.uit.finance.modules.recurring.application.service;

import com.uit.finance.modules.recurring.application.port.in.PurgeUserRecurringsUseCase;
import com.uit.finance.modules.recurring.application.port.out.PurgeRecurringsPort;
import com.uit.finance.shared.kernel.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Một câu DELETE trong một transaction. Không cần khoá ghi của user: event chỉ được phát khi không
 * còn token nào của user ghi được (ADR-008 A7).
 */
@Service
@Transactional
class PurgeUserRecurringsService implements PurgeUserRecurringsUseCase {

  private final PurgeRecurringsPort recurrings;

  PurgeUserRecurringsService(PurgeRecurringsPort recurrings) {
    this.recurrings = recurrings;
  }

  @Override
  public long purge(UserId owner) {
    return recurrings.deleteEverythingOf(owner);
  }
}
