package com.uit.finance.modules.ledger.application.port.in.account;

import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;

/**
 * Tạo ví theo id do client sinh (ADR-002).
 *
 * <ul>
 *   <li>Gửi lại cùng id: no-op, trả bản đang có, để retry an toàn. Muốn đổi dữ liệu thì dùng {@link
 *       UpdateAccountUseCase}.
 *   <li>Id đã thuộc user khác: {@code ledger.not_found}, không ghi đè (ADR-006 B1).
 *   <li>Id đã bị xoá: {@code ledger.deleted}, xoá luôn thắng (ADR-002 S4).
 * </ul>
 */
public interface CreateAccountUseCase {

  AccountView create(CreateAccountCommand command);

  /** {@code userId} lấy từ token, không bao giờ từ body (ADR-006 B3). */
  record CreateAccountCommand(UserId userId, UUID accountId, AccountFields fields) {}
}
