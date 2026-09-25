package com.mosaicglobal.finance.modules.identity.application.service;

import com.mosaicglobal.finance.modules.identity.application.port.in.ProvisionUserUseCase;
import com.mosaicglobal.finance.modules.identity.application.port.out.LoadUserPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.SaveUserPort;
import com.mosaicglobal.finance.modules.identity.domain.exception.UserProvisioningFailedException;
import com.mosaicglobal.finance.modules.identity.domain.model.DisplayName;
import com.mosaicglobal.finance.modules.identity.domain.model.Email;
import com.mosaicglobal.finance.modules.identity.domain.model.ExternalSubject;
import com.mosaicglobal.finance.modules.identity.domain.model.User;
import com.mosaicglobal.finance.shared.kernel.DomainEventPublisher;
import com.mosaicglobal.finance.shared.kernel.UserId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JIT provisioning: request đầu tiên của một {@code sub} tạo local account; các lần sau chỉ update
 * khi Keycloak trả về profile đã đổi.
 */
@Service
@Transactional
class ProvisionUserService implements ProvisionUserUseCase {

  private final LoadUserPort loadUser;
  private final SaveUserPort saveUser;
  private final DomainEventPublisher events;
  private final Clock clock;

  ProvisionUserService(
      LoadUserPort loadUser, SaveUserPort saveUser, DomainEventPublisher events, Clock clock) {
    this.loadUser = loadUser;
    this.saveUser = saveUser;
    this.events = events;
    this.clock = clock;
  }

  @Override
  public UserId provision(ProvisionUserCommand command) {
    ExternalSubject subject = ExternalSubject.of(command.externalSubject());
    Email email = Email.of(command.email());
    DisplayName displayName = DisplayName.of(command.displayName());

    return loadUser
        .byExternalSubject(subject)
        .map(existing -> refreshProfile(existing, email, displayName))
        .orElseGet(() -> create(subject, email, displayName));
  }

  private UserId create(ExternalSubject subject, Email email, DisplayName displayName) {
    User user = User.provision(UserId.newId(), subject, email, displayName, clock.instant());
    if (saveUser.insertIfAbsent(user)) {
      events.publishAll(user.pullDomainEvents());
      return user.getId();
    }
    // Request khác vừa thắng race: adopt account nó vừa tạo thay vì throw lỗi.
    return loadUser
        .byExternalSubject(subject)
        .map(User::getId)
        .orElseThrow(() -> new UserProvisioningFailedException(subject.value()));
  }

  private UserId refreshProfile(User user, Email email, DisplayName displayName) {
    User updated = user.withProfile(email, displayName);
    if (updated != user) {
      saveUser.update(updated);
    }
    return user.getId();
  }
}
