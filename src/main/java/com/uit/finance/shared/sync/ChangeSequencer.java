package com.uit.finance.shared.sync;

import com.uit.finance.shared.kernel.UserId;

/**
 * Phát số thứ tự thay đổi ({@code change_seq}) theo từng user, làm cursor cho sync pull (ADR-002
 * §2).
 *
 * <p>Một dãy số chung cho mọi entity của user. Số được phát bằng row lock **trong transaction của
 * lần ghi**, nên thứ tự số = thứ tự commit và pull {@code change_seq > since} không bao giờ sót.
 * Transaction rollback thì số cũng được trả lại, dãy không có lỗ.
 *
 * <p>Interface ở đây, implementation ở {@code modules.sync} (sở hữu bảng {@code user_sync_state}):
 * module ghi dữ liệu không cần biết sync tồn tại, cùng kiểu với {@code CurrentUserResolver}. Chỉ
 * adapter persistence được dùng (ArchUnit luật #7).
 */
public interface ChangeSequencer {

  /** Số kế tiếp cho một lần ghi. */
  default long next(UserId owner) {
    return reserve(owner, 1);
  }

  /**
   * Giữ chỗ {@code count} số liên tiếp cho một lần ghi nhiều dòng.
   *
   * @return số đầu tiên của dãy; dãy là {@code [first, first + count)}
   */
  long reserve(UserId owner, int count);

  /**
   * Khoá dãy số của user tới hết transaction mà không lấy số. Dùng khi phải đọc trước để biết sẽ
   * ghi bao nhiêu dòng, để không phát thừa số (tạo lỗ trong dãy).
   */
  void lock(UserId owner);
}
