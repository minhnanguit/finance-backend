package com.uit.finance.modules.identity.adapter.in.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uit.finance.modules.identity.adapter.config.IdentityProperties;
import com.uit.finance.modules.identity.application.port.in.CheckUserStatusUseCase;
import com.uit.finance.modules.identity.application.port.in.ProvisionUserUseCase;
import com.uit.finance.modules.identity.application.port.in.ProvisionUserUseCase.ProvisionUserCommand;
import com.uit.finance.modules.identity.domain.event.UserDeletionRequested;
import com.uit.finance.modules.identity.domain.exception.UserDeletedException;
import com.uit.finance.shared.kernel.UserId;
import com.uit.finance.shared.security.CurrentUserResolver;
import com.uit.finance.shared.security.InactiveAccountException;
import com.uit.finance.shared.security.SubjectClaims;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Plug {@code identity} vào SPI {@link CurrentUserResolver} của security layer.
 *
 * <p>Nằm trên hot path — mọi request có token đều qua đây — nên cache hit chỉ tốn một lần lookup
 * in-memory. {@link Cache#get(Object, java.util.function.Function)} gộp các concurrent miss của
 * cùng một {@code sub} thành một lần gọi, nên burst request đầu tiên chỉ provision một lần.
 *
 * <p>Tài khoản đã xoá (ADR-008 A6) trả {@code 403 identity.user_deleted} ở mọi endpoint. Mapping
 * {@code sub} → id không bao giờ đổi nên cache lâu; riêng trạng thái "đã xoá" được hỏi lại DB sau
 * mỗi {@code accountStatusCacheTtl} (mặc định 30 giây, một lần đọc theo khoá chính cho mỗi user
 * đang dùng API). Instance nhận yêu cầu xoá thì biết ngay sau khi commit.
 */
@Component
class ProvisioningCurrentUserResolver implements CurrentUserResolver {

  private final ProvisionUserUseCase provisionUser;
  private final CheckUserStatusUseCase userStatus;
  private final Cache<String, UUID> resolvedSubjects;
  private final Cache<UUID, Boolean> deletedUsers;

  ProvisioningCurrentUserResolver(
      ProvisionUserUseCase provisionUser,
      CheckUserStatusUseCase userStatus,
      IdentityProperties properties) {
    this.provisionUser = provisionUser;
    this.userStatus = userStatus;
    this.resolvedSubjects =
        Caffeine.newBuilder()
            .maximumSize(properties.subjectCacheSize())
            .expireAfterWrite(properties.subjectCacheTtl())
            .build();
    this.deletedUsers =
        Caffeine.newBuilder()
            .maximumSize(properties.subjectCacheSize())
            .expireAfterWrite(properties.accountStatusCacheTtl())
            .build();
  }

  @Override
  public UserId resolve(SubjectClaims claims) {
    UUID userId;
    try {
      userId =
          resolvedSubjects.get(
              claims.subject(),
              subject ->
                  provisionUser
                      .provision(
                          new ProvisionUserCommand(subject, claims.email(), claims.displayName()))
                      .value());
    } catch (UserDeletedException e) {
      throw accountDeleted();
    }
    if (deletedUsers.get(userId, id -> userStatus.isDeleted(new UserId(id)))) {
      throw accountDeleted();
    }
    return new UserId(userId);
  }

  /** Sau khi yêu cầu xoá đã commit: instance này chặn tài khoản ngay, không đợi hết TTL. */
  @TransactionalEventListener
  void onDeletionRequested(UserDeletionRequested event) {
    deletedUsers.put(event.userId(), Boolean.TRUE);
  }

  private static InactiveAccountException accountDeleted() {
    return new InactiveAccountException(UserDeletedException.CODE, "This account has been deleted");
  }
}
