package com.uit.finance.modules.sync.application.port.out;

import com.uit.finance.modules.sync.domain.model.OpLogEntry;
import com.uit.finance.modules.sync.domain.model.OpOutcome;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Bảng {@code sync_ops}: chống gửi trùng và nhật ký. */
public interface OpLogPort {

  Optional<OpOutcome> findOutcome(UserId owner, UUID opId);

  /**
   * Ghi kết quả của op. Chỉ ghi đè được dòng đang {@code RETRY}; kết quả đã chốt giữ nguyên. Không
   * ném exception khi trùng, để không làm transaction thành rollback-only.
   *
   * @return {@code false} khi op đã có kết quả chốt (một request khác đã xử lý nó)
   */
  boolean record(UserId owner, OpLogEntry entry);

  /**
   * Xoá tối đa {@code batchSize} dòng cũ hơn {@code cutoff}. Xoá theo lô để không khoá bảng lâu.
   *
   * @return số dòng đã xoá
   */
  int purgeProcessedBefore(Instant cutoff, int batchSize);
}
