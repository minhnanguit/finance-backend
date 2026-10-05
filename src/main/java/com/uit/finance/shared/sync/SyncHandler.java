package com.uit.finance.shared.sync;

import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cầu nối giữa module {@code sync} và một loại entity đồng bộ được (ADR-002 §6).
 *
 * <p>Module có dữ liệu (ledger, sau này budget...) cung cấp một bean cho mỗi entity; {@code sync}
 * nhận cả danh sách qua Spring và không biết module nào tồn tại. Thêm entity mới = thêm một
 * handler, không sửa {@code sync}.
 *
 * <p>Mọi method chạy trong transaction do {@code sync} mở và luôn nhận {@code owner} lấy từ token
 * (ADR-006 B1, B3).
 */
public interface SyncHandler {

  /** Tên entity trên contract, ví dụ {@code account}. Duy nhất trong toàn hệ thống. */
  String entity();

  /**
   * Áp một op. **Không ném lỗi nghiệp vụ ra ngoài**: chỉ module sở hữu biết mã lỗi nào nghĩa là
   * {@code RETRY}, {@code CONFLICT} hay {@code REJECTED}, nên việc dịch nằm ở đây.
   */
  SyncOpResult apply(UserId owner, SyncOp op);

  /** Bản ghi hiện tại của chính {@code owner}, kể cả tombstone. Của user khác thì rỗng (B2). */
  Optional<SyncChange> current(UserId owner, UUID id);

  /** Tối đa {@code limit} thay đổi có {@code changeSeq > afterSeq}, tăng dần. */
  List<SyncChange> changesSince(UserId owner, long afterSeq, int limit);

  /** Chạy một lần ở lần sync đầu tiên của user (ADR-002 §5). Phải idempotent. */
  default void initialize(UserId owner) {}
}
