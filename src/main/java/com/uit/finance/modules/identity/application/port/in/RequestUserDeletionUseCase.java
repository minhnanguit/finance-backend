package com.uit.finance.modules.identity.application.port.in;

import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * User tự xoá tài khoản (ADR-008 A1–A4). Ghi nhận ngay, phần còn lại chạy ở nền: tài khoản Keycloak
 * bị xoá bởi {@link RemoveIdentityProviderAccountsUseCase}, dữ liệu các module bị xoá khi có {@code
 * identity.user.deleted}.
 */
public interface RequestUserDeletionUseCase {

  /**
   * @return thời điểm đã ghi nhận yêu cầu
   * @throws com.uit.finance.modules.identity.domain.exception.ReauthenticationRequiredException lần
   *     đăng nhập gần nhất quá 5 phút
   * @throws com.uit.finance.modules.identity.domain.exception.UserDeletedException đã xoá rồi
   */
  Instant requestDeletion(RequestUserDeletionCommand command);

  /**
   * @param authenticatedAt claim {@code auth_time} của token đang gọi; {@code null} khi token không
   *     có, coi như chưa đăng nhập lại
   */
  record RequestUserDeletionCommand(UserId userId, @Nullable Instant authenticatedAt) {}
}
