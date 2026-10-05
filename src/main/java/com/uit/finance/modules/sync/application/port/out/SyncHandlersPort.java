package com.uit.finance.modules.sync.application.port.out;

import com.uit.finance.modules.sync.domain.model.ChangeRecord;
import com.uit.finance.modules.sync.domain.model.OpVerdict;
import com.uit.finance.modules.sync.domain.model.PushedOp;
import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Các module có dữ liệu đồng bộ, nhìn từ phía sync. Adapter chuyển tới {@code SyncHandler} trong
 * {@code shared.sync}; application không đụng SPI đó trực tiếp (ArchUnit luật #7).
 */
public interface SyncHandlersPort {

  boolean supports(String entity);

  OpVerdict apply(UserId owner, PushedOp op);

  /** Rỗng khi entity không có, bản ghi không có, hoặc là của user khác. */
  Optional<ChangeRecord> current(UserId owner, String entity, UUID id);

  /** Thay đổi của mọi entity có {@code changeSeq > afterSeq}, tăng dần, tối đa {@code limit}. */
  List<ChangeRecord> changesSince(UserId owner, long afterSeq, int limit);

  void initialize(UserId owner);
}
