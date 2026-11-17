package com.uit.finance.modules.identity.application.port.in;

import com.uit.finance.shared.kernel.UserId;

/**
 * Tài khoản đã bị xoá chưa (ADR-008 A6). Security layer hỏi theo chu kỳ ngắn cho mỗi user đang dùng
 * API, nên phải là một lần đọc theo khoá chính.
 */
public interface CheckUserStatusUseCase {

  /** {@code false} cả khi không tìm thấy user: request sẽ tự lỗi ở bước sau. */
  boolean isDeleted(UserId userId);
}
