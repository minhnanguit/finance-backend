package com.mosaicglobal.finance.modules.identity.application.service;

import static com.mosaicglobal.finance.modules.identity.application.service.IdentityFakes.CLOCK;
import static com.mosaicglobal.finance.modules.identity.application.service.IdentityFakes.DEVICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mosaicglobal.finance.modules.identity.application.port.in.AuthTokens;
import com.mosaicglobal.finance.modules.identity.application.port.in.RegisterUserUseCase.RegisterUserCommand;
import com.mosaicglobal.finance.modules.identity.application.service.IdentityFakes.FakePasswordHasher;
import com.mosaicglobal.finance.modules.identity.application.service.IdentityFakes.InMemoryRefreshTokens;
import com.mosaicglobal.finance.modules.identity.application.service.IdentityFakes.InMemoryUsers;
import com.mosaicglobal.finance.modules.identity.application.service.IdentityFakes.RecordingEventPublisher;
import com.mosaicglobal.finance.modules.identity.application.service.IdentityFakes.SequentialRefreshTokenGenerator;
import com.mosaicglobal.finance.modules.identity.domain.event.UserRegistered;
import com.mosaicglobal.finance.modules.identity.domain.exception.EmailAlreadyRegisteredException;
import com.mosaicglobal.finance.modules.identity.domain.model.Email;
import com.mosaicglobal.finance.modules.identity.domain.model.TokenHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegisterUserServiceTest {

  private InMemoryUsers users;
  private InMemoryRefreshTokens refreshTokens;
  private RecordingEventPublisher events;
  private RegisterUserService service;

  @BeforeEach
  void setUp() {
    users = new InMemoryUsers();
    refreshTokens = new InMemoryRefreshTokens();
    events = new RecordingEventPublisher();
    SequentialRefreshTokenGenerator generator = new SequentialRefreshTokenGenerator();
    service =
        new RegisterUserService(
            users,
            users,
            new FakePasswordHasher(),
            events,
            IdentityFakes.sessionIssuer(generator, refreshTokens),
            CLOCK);
  }

  @Test
  void registersUserOpensSessionAndPublishesEvent() {
    AuthTokens tokens =
        service.register(
            new RegisterUserCommand("Alice@Example.com", "correct horse", "Alice", DEVICE));

    assertThat(users.byEmail(Email.of("alice@example.com")))
        .hasValueSatisfying(
            u -> {
              assertThat(u.getPasswordHash().value()).isEqualTo("hashed:correct horse");
              assertThat(u.getCreatedAt()).isEqualTo(IdentityFakes.NOW);
            });
    assertThat(events.published).singleElement().isInstanceOf(UserRegistered.class);
    assertThat(tokens.refreshToken()).isEqualTo("raw-1");
    assertThat(tokens.accessToken()).startsWith("jwt:");
    // Only the hash is stored; the raw token never touches persistence.
    assertThat(refreshTokens.byHash(new TokenHash("sha:raw-1"))).isPresent();
  }

  @Test
  void rejectsDuplicateEmail() {
    service.register(
        new RegisterUserCommand("alice@example.com", "correct horse", "Alice", DEVICE));

    assertThatThrownBy(
            () ->
                service.register(
                    new RegisterUserCommand(
                        "ALICE@example.com", "another pass", "Alice 2", DEVICE)))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
    assertThat(events.published).hasSize(1);
  }

  @Test
  void enforcesPasswordPolicyBeforeTouchingAnyPort() {
    assertThatThrownBy(
            () -> service.register(new RegisterUserCommand("a@b.co", "short", "Alice", DEVICE)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(users.byId).isEmpty();
    assertThat(events.published).isEmpty();
  }
}
