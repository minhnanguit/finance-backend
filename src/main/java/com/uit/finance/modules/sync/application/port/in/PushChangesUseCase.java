package com.uit.finance.modules.sync.application.port.in;

import com.uit.finance.modules.sync.domain.model.OpResult;
import com.uit.finance.modules.sync.domain.model.PushedOp;
import com.uit.finance.shared.kernel.UserId;
import java.util.List;

/**
 * Áp các op client gửi lên (ADR-002 §4, §7). Mỗi op một transaction; kết quả trả theo đúng thứ tự
 * op. Lần sync đầu tiên của user chạy khởi tạo trước (ADR-002 §5).
 */
public interface PushChangesUseCase {

  List<OpResult> push(PushCommand command);

  /** {@code userId} lấy từ token (ADR-006 B3). */
  record PushCommand(UserId userId, String deviceId, List<PushedOp> ops) {

    public PushCommand {
      ops = List.copyOf(ops);
    }
  }
}
