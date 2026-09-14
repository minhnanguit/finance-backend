package com.mosaicglobal.finance.modules.identity.application.service;

import static com.mosaicglobal.finance.modules.identity.application.service.IdentityFakes.CLOCK;
import static com.mosaicglobal.finance.modules.identity.application.service.IdentityFakes.DEVICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mosaicglobal.finance.modules.identity.application.port.in.AuthTokens;
import com.mosaicglobal.finance.modules.identity.application.port.in.AuthenticateUserUseCase.AuthenticateUserCommand;
import com.mosaicglobal.finance.modules.identity.application.service.IdentityFakes.FakePasswordHasher;
import com.mosaicglobal.finance.modules.identity.application.service.IdentityFakes.InMemoryRefreshTokens;
import com.mosaicglobal.finance.modules.identity.application.service.IdentityFakes.InMemoryUsers;
import com.mosaicglobal.finance.modules.identity.application.service.IdentityFakes.SequentialRefreshTokenGenerator;
import com.mosaicglobal.finance.modules.identity.domain.exception.InvalidCredentialsException;
import com.mosaicglobal.finance.modules.identity.domain.model.DisplayName;
import com.mosaicglobal.finance.modules.identity.domain.model.Email;
import com.mosaicglobal.finance.modules.identity.domain.model.PasswordHash;
import com.mosaicglobal.finance.modules.identity.domain.model.User;
import com.mosaicglobal.finance.modules.identity.domain.model.UserStatus;
import com.mosaicglobal.finance.shared.kernel.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthenticateUserServiceTest {

  private InMemoryUsers users;
  private FakePasswordHasher hasher;
  private AuthenticateUserService service;

  @BeforeEach
  void setUp() {
    users = new InMemoryUsers();
    hasher = new FakePasswordHasher();
    service =
        new AuthenticateUserService(
            users,
            hasher,
            IdentityFakes.sessionIssuer(
                new SequentialRefreshTokenGenerator(), new InMemoryRefreshTokens()),
            CLOCK);
    users.save(
        User.rehydrate(
            UserId.newId(),
            Email.of("alice@example.com"),
            new PasswordHash("hashed:correct horse"),
            DisplayName.of("Alice"),
            UserStatus.ACTIVE,
            IdentityFakes.NOW));
  }

  @Test
  void issuesTokensForValidCredentials() {
    AuthTokens tokens =
        service.authenticate(
            new AuthenticateUserCommand("alice@example.com", "correct horse", DEVICE));
    assertThat(tokens.accessToken()).startsWith("jwt:");
    assertThat(tokens.refreshToken()).isEqualTo("raw-1");
  }

  @Test
  void rejectsWrongPassword() {
    assertThatThrownBy(
            () ->
                service.authenticate(
                    new AuthenticateUserCommand("alice@example.com", "wrong pass", DEVICE)))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void unknownEmailStillRunsTheHashComparison() {
    assertThatThrownBy(
            () ->
                service.authenticate(
                    new AuthenticateUserCommand("nobody@example.com", "whatever1", DEVICE)))
        .isInstanceOf(InvalidCredentialsException.class);
    assertThat(hasher.matchCalls.get()).isEqualTo(1);
  }
}
