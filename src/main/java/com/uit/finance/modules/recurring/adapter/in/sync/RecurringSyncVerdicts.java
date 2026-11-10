package com.uit.finance.modules.recurring.adapter.in.sync;

import com.uit.finance.modules.recurring.domain.exception.RecurringDeletedException;
import com.uit.finance.modules.recurring.domain.exception.RecurringReferencePendingException;
import com.uit.finance.modules.recurring.domain.exception.RevisionConflictException;
import com.uit.finance.shared.kernel.DomainException;
import com.uit.finance.shared.sync.SyncOpResult;
import java.util.function.Supplier;
import org.springframework.dao.ConcurrencyFailureException;

/** Dịch lỗi của recurring ra kết quả sync (ADR-008 §6, theo mẫu {@code BudgetSyncVerdicts}). */
final class RecurringSyncVerdicts {

  static final String CONCURRENT_UPDATE = "recurring.concurrent_update";

  private RecurringSyncVerdicts() {}

  static SyncOpResult guard(Supplier<SyncOpResult> apply) {
    try {
      return apply.get();
    } catch (DomainException e) {
      return fromCode(e.code());
    } catch (ConcurrencyFailureException e) {
      return SyncOpResult.retry(CONCURRENT_UPDATE);
    }
  }

  static SyncOpResult fromCode(String code) {
    return switch (code) {
      // Ví/danh mục chưa tới, hoặc của user khác: client thử lại, không phân biệt được (B2).
      case RecurringReferencePendingException.CODE -> SyncOpResult.retry(code);
      // Xoá luôn thắng (S4); client dựa revision cũ (RS8): server giữ bản của mình.
      case RecurringDeletedException.CODE, RevisionConflictException.CODE ->
          SyncOpResult.conflict(code);
      // Còn lại, kể cả sync.unknown_field / sync.invalid_field của SyncData.
      default -> SyncOpResult.rejected(code);
    };
  }
}
