package com.uit.finance.modules.ledger.application.service.maintenance;

import com.uit.finance.modules.ledger.application.port.in.maintenance.PurgeUserLedgerUseCase;
import com.uit.finance.modules.ledger.application.port.out.maintenance.PurgeLedgerPort;
import com.uit.finance.shared.kernel.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Một transaction cho cả sổ: hoặc xoá hết, hoặc không xoá gì (consumer thử lại). Không cần khoá dãy
 * số của user: event chỉ được phát khi không còn token nào của user ghi được (ADR-008 A7).
 */
@Service
@Transactional
class PurgeUserLedgerService implements PurgeUserLedgerUseCase {

  private final PurgeLedgerPort ledger;

  PurgeUserLedgerService(PurgeLedgerPort ledger) {
    this.ledger = ledger;
  }

  @Override
  public long purge(UserId owner) {
    return ledger.deleteEverythingOf(owner);
  }
}
