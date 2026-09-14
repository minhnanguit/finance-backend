package com.mosaicglobal.finance.modules.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mosaicglobal.finance.modules.identity.domain.event.UserRegistered;
import com.mosaicglobal.finance.modules.identity.domain.exception.InvalidCredentialsException;
import com.mosaicglobal.finance.shared.kernel.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {

  private static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");

  @Test
  void registeringRecordsAUserRegisteredEventOnce() {
    User user =
        User.register(
            UserId.newId(),
            Email.of("alice@example.com"),
            new PasswordHash("{noop}x"),
            DisplayName.of("Alice"),
            NOW);

    var events = user.pullDomainEvents();
    assertThat(events).hasSize(1);
    UserRegistered event = (UserRegistered) events.getFirst();
    assertThat(event.type()).isEqualTo("identity.user.registered");
    assertThat(event.userId()).isEqualTo(user.getId().value());
    assertThat(event.email()).isEqualTo("alice@example.com");
    assertThat(event.occurredAt()).isEqualTo(NOW);
    assertThat(user.pullDomainEvents()).isEmpty();
  }

  @Test
  void rehydratedUserRecordsNoEvents() {
    User user =
        User.rehydrate(
            UserId.newId(),
            Email.of("a@b.co"),
            new PasswordHash("{noop}x"),
            DisplayName.of("A"),
            UserStatus.ACTIVE,
            NOW);
    assertThat(user.pullDomainEvents()).isEmpty();
  }

  @Test
  void disabledUserCannotAuthenticateAndDoesNotLeakWhy() {
    User user =
        User.rehydrate(
            UserId.newId(),
            Email.of("a@b.co"),
            new PasswordHash("{noop}x"),
            DisplayName.of("A"),
            UserStatus.DISABLED,
            NOW);
    assertThatThrownBy(user::ensureCanAuthenticate).isInstanceOf(InvalidCredentialsException.class);
  }
}
