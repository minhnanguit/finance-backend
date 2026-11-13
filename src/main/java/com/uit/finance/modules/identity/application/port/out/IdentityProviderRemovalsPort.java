package com.uit.finance.modules.identity.application.port.out;

import com.uit.finance.modules.identity.domain.model.ExternalSubject;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.List;

/**
 * Hàng đợi bền của việc xoá tài khoản bên Keycloak (ADR-008 A8): mọi tài khoản đang/đã xoá mà chưa
 * được đánh dấu xoá xong bên IdP. Nằm ngay trên dòng {@code users} nên không mất khi Keycloak lỗi
 * lâu, broker mất message hay instance khởi động lại.
 */
public interface IdentityProviderRemovalsPort {

  /** Tối đa {@code limit} việc còn nợ, yêu cầu xoá sớm nhất trước. */
  List<PendingRemoval> pending(int limit);

  /** Đánh dấu tài khoản bên IdP đã bị xoá. Gọi lại (hai instance cùng làm) không sao. */
  void markRemoved(UserId userId, Instant removedAt);

  record PendingRemoval(UserId userId, ExternalSubject subject, Instant deletionRequestedAt) {}
}
