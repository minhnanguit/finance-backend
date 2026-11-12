package com.uit.finance.modules.identity.application.service;

import com.uit.finance.modules.identity.application.port.in.RequestUserDeletionUseCase;
import com.uit.finance.modules.identity.application.port.out.LoadUserPort;
import com.uit.finance.modules.identity.application.port.out.SaveUserPort;
import com.uit.finance.modules.identity.domain.exception.UserNotFoundException;
import com.uit.finance.modules.identity.domain.model.ReauthenticationPolicy;
import com.uit.finance.modules.identity.domain.model.User;
import com.uit.finance.shared.kernel.DomainEventPublisher;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bước đồng bộ duy nhất của xoá tài khoản (ADR-008 A4): đổi trạng thái, xoá thông tin cá nhân và
 * ghi event vào outbox trong một transaction. Không gọi Keycloak ở đây: tài khoản vừa vào hàng đợi
 * {@code users.idp_removed_at IS NULL}, job {@code IdentityProviderRemovalJob} xoá bên Keycloak
 * ngoài mọi transaction DB.
 */
@Service
@Transactional
class RequestUserDeletionService implements RequestUserDeletionUseCase {

  private final LoadUserPort loadUser;
  private final SaveUserPort saveUser;
  private final DomainEventPublisher events;
  private final Clock clock;

  RequestUserDeletionService(
      LoadUserPort loadUser, SaveUserPort saveUser, DomainEventPublisher events, Clock clock) {
    this.loadUser = loadUser;
    this.saveUser = saveUser;
    this.events = events;
    this.clock = clock;
  }

  @Override
  public Instant requestDeletion(RequestUserDeletionCommand command) {
    Instant now = clock.instant();
    User user =
        loadUser
            .byId(command.userId())
            .orElseThrow(() -> new UserNotFoundException(command.userId()));
    user.requireNotDeleted();
    ReauthenticationPolicy.requireRecent(command.authenticatedAt(), now);

    User deleting = user.requestDeletion(now);
    saveUser.update(deleting);
    events.publishAll(deleting.pullDomainEvents());
    return now;
  }
}
