package com.mosaicglobal.finance.modules.identity.application.service;

import com.mosaicglobal.finance.modules.identity.application.port.in.AuthTokens;
import com.mosaicglobal.finance.modules.identity.application.port.in.RegisterUserUseCase;
import com.mosaicglobal.finance.modules.identity.application.port.out.LoadUserPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.PasswordHasherPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.SaveUserPort;
import com.mosaicglobal.finance.modules.identity.domain.exception.EmailAlreadyRegisteredException;
import com.mosaicglobal.finance.modules.identity.domain.model.Device;
import com.mosaicglobal.finance.modules.identity.domain.model.DisplayName;
import com.mosaicglobal.finance.modules.identity.domain.model.Email;
import com.mosaicglobal.finance.modules.identity.domain.model.RawPassword;
import com.mosaicglobal.finance.modules.identity.domain.model.User;
import com.mosaicglobal.finance.shared.kernel.DomainEventPublisher;
import com.mosaicglobal.finance.shared.kernel.UserId;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class RegisterUserService implements RegisterUserUseCase {

  private final LoadUserPort loadUser;
  private final SaveUserPort saveUser;
  private final PasswordHasherPort passwordHasher;
  private final DomainEventPublisher events;
  private final SessionIssuer sessions;
  private final Clock clock;

  RegisterUserService(
      LoadUserPort loadUser,
      SaveUserPort saveUser,
      PasswordHasherPort passwordHasher,
      DomainEventPublisher events,
      SessionIssuer sessions,
      Clock clock) {
    this.loadUser = loadUser;
    this.saveUser = saveUser;
    this.passwordHasher = passwordHasher;
    this.events = events;
    this.sessions = sessions;
    this.clock = clock;
  }

  @Override
  public AuthTokens register(RegisterUserCommand command) {
    Email email = Email.of(command.email());
    RawPassword password = RawPassword.forRegistration(command.rawPassword());
    DisplayName displayName = DisplayName.of(command.displayName());
    Device device = DeviceMapper.toDevice(command.device());

    if (loadUser.byEmail(email).isPresent()) {
      throw new EmailAlreadyRegisteredException();
    }

    Instant now = clock.instant();
    User user =
        User.register(UserId.newId(), email, passwordHasher.hash(password), displayName, now);
    saveUser.save(user); // the unique index is the final arbiter of a concurrent duplicate
    events.publishAll(user.pullDomainEvents());

    return sessions.open(user, device, now);
  }
}
