package com.uit.finance.modules.ledger.adapter.in.sync.support;

import com.uit.finance.modules.ledger.domain.exception.EntityDeletedException;
import com.uit.finance.modules.ledger.domain.exception.ReferencePendingException;
import com.uit.finance.shared.kernel.DomainException;
import com.uit.finance.shared.sync.SyncOpResult;
import java.util.function.Supplier;
import org.springframework.dao.ConcurrencyFailureException;

/**
 * Dịch lỗi của ledger ra kết quả sync (ADR-002 §4, ADR-005 §3). Chỉ ledger biết mã nào nghĩa là gì,
 * nên việc dịch nằm ở đây chứ không ở module sync.
 */
public final class LedgerSyncVerdicts {

  /** Hai lần sửa cùng một bản ghi chạy song song; lần thử lại sẽ đọc bản mới và thắng (S3). */
  public static final String CONCURRENT_UPDATE = "ledger.concurrent_update";

  private LedgerSyncVerdicts() {}

  /**
   * Chạy một lần áp op. Lỗi nghiệp vụ không bay ra ngoài; module sync sẽ rollback mọi thứ đã ghi dở
   * vì kết quả khác {@code APPLIED}.
   */
  public static SyncOpResult guard(Supplier<SyncOpResult> apply) {
    try {
      return apply.get();
    } catch (DomainException e) {
      return fromCode(e.code());
    } catch (ConcurrencyFailureException e) {
      return SyncOpResult.retry(CONCURRENT_UPDATE);
    }
  }

  public static SyncOpResult fromCode(String code) {
    return switch (code) {
      // Ví/danh mục chưa tới, hoặc của user khác: client thử lại, không phân biệt được (B2).
      case ReferencePendingException.CODE -> SyncOpResult.retry(code);
      // Xoá luôn thắng (S4).
      case EntityDeletedException.CODE -> SyncOpResult.conflict(code);
      default -> SyncOpResult.rejected(code);
    };
  }
}
