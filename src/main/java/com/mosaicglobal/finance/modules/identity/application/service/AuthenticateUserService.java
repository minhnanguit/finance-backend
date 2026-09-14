package com.mosaicglobal.finance.modules.identity.application.service;

import com.mosaicglobal.finance.modules.identity.application.port.in.AuthTokens;
import com.mosaicglobal.finance.modules.identity.application.port.in.AuthenticateUserUseCase;
import com.mosaicglobal.finance.modules.identity.application.port.out.LoadUserPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.PasswordHasherPort;
import com.mosaicglobal.finance.modules.identity.domain.exception.InvalidCredentialsException;
import com.mosaicglobal.finance.modules.identity.domain.model.Device;
import com.mosaicglobal.finance.modules.identity.domain.model.Email;
import com.mosaicglobal.finance.modules.identity.domain.model.PasswordHash;
import com.mosaicglobal.finance.modules.identity.domain.model.RawPassword;
import com.mosaicglobal.finance.modules.identity.domain.model.User;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class AuthenticateUserService implements AuthenticateUserUseCase {

  private final LoadUserPort loadUser;
  private final PasswordHasherPort passwordHasher;
  private final SessionIssuer sessions;
  private final Clock clock;

  AuthenticateUserService(
      LoadUserPort loadUser,
      PasswordHasherPort passwordHasher,
      SessionIssuer sessions,
      Clock clock) {
    this.loadUser = loadUser;
    this.passwordHasher = passwordHasher;
    this.sessions = sessions;
    this.clock = clock;
  }

  @Override
  public AuthTokens authenticate(AuthenticateUserCommand command) {
    Email email = Email.of(command.email());
    RawPassword password = RawPassword.forVerification(command.rawPassword());
    Device device = DeviceMapper.toDevice(command.device());

    Optional<User> user = loadUser.byEmail(email);
    // Always run the hash comparison so response time does not reveal whether the e-mail exists.
    PasswordHash candidate =
        user.map(User::getPasswordHash).orElseGet(passwordHasher::placeholderHash);
    boolean passwordOk = passwordHasher.matches(password, candidate);
    if (user.isEmpty() || !passwordOk) {
      throw new InvalidCredentialsException();
    }
    User authenticated = user.get();
    authenticated.ensureCanAuthenticate();

    return sessions.open(authenticated, device, clock.instant());
  }
}
