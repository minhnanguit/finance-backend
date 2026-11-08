package com.uit.finance.modules.budget.adapter.in.sync;

import com.uit.finance.modules.budget.domain.exception.BudgetDeletedException;
import com.uit.finance.modules.budget.domain.exception.BudgetReferencePendingException;
import com.uit.finance.shared.kernel.DomainException;
import com.uit.finance.shared.sync.SyncOpResult;
import java.util.function.Supplier;
import org.springframework.dao.ConcurrencyFailureException;

/**
 * Dịch lỗi của budget ra kết quả sync (ADR-007 §5, theo mẫu {@code LedgerSyncVerdicts}). Chỉ budget
 * biết mã nào nghĩa là gì, nên việc dịch nằm ở đây chứ không ở module sync.
 */
final class BudgetSyncVerdicts {

  /** Hai lần sửa cùng một ngân sách đụng optimistic lock; lần thử lại sẽ thắng (S3). */
  static final String CONCURRENT_UPDATE = "budget.concurrent_update";

  private BudgetSyncVerdicts() {}

  /**
   * Chạy một lần áp op. Lỗi nghiệp vụ không bay ra ngoài; module sync rollback mọi thứ đã ghi dở vì
   * kết quả khác {@code APPLIED}.
   */
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
      // Danh mục chưa tới, hoặc của user khác: client thử lại, không phân biệt được (B2).
      case BudgetReferencePendingException.CODE -> SyncOpResult.retry(code);
      // Xoá luôn thắng (S4).
      case BudgetDeletedException.CODE -> SyncOpResult.conflict(code);
      // Còn lại, kể cả sync.unknown_field / sync.invalid_field của SyncData.
      default -> SyncOpResult.rejected(code);
    };
  }
}
