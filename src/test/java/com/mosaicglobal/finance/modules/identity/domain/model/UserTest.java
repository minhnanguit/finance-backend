package com.mosaicglobal.finance.modules.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.mosaicglobal.finance.modules.identity.domain.event.UserRegistered;
import com.mosaicglobal.finance.shared.kernel.UserId;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

  private static final Instant NOW = Instant.parse("2026-09-20T10:15:30Z");

  private static User provisioned() {
    return User.provision(
        UserId.newId(),
        ExternalSubject.of("keycloak-sub-1"),
        Email.of("ann@example.com"),
        com.mosaicglobal.finance.modules.identity.domain.model.DisplayName.of("Ann"),
        NOW);
  }

  @Test
  @DisplayName("provisioning records a registration event exactly once")
  void provisioningRecordsEvent() {
    User user = provisioned();

    assertThat(user.pullDomainEvents()).singleElement().isInstanceOf(UserRegistered.class);
    assertThat(user.pullDomainEvents()).isEmpty();
  }

  @Test
  @DisplayName("rehydrating from storage records nothing")
  void rehydrateIsSilent() {
    User user =
        User.rehydrate(
            UserId.newId(),
            ExternalSubject.of("keycloak-sub-1"),
            Email.of("ann@example.com"),
            com.mosaicglobal.finance.modules.identity.domain.model.DisplayName.of("Ann"),
            UserStatus.ACTIVE,
            NOW);

    assertThat(user.pullDomainEvents()).isEmpty();
    assertThat(user.isActive()).isTrue();
  }

  @Test
  @DisplayName("an unchanged profile returns the same instance so callers can skip the write")
  void unchangedProfileReturnsSameInstance() {
    User user = provisioned();

    User same =
        user.withProfile(
            Email.of("ann@example.com"),
            com.mosaicglobal.finance.modules.identity.domain.model.DisplayName.of("Ann"));

    assertThat(same).isSameAs(user);
  }

  @Test
  @DisplayName("a changed profile yields a new instance keeping id, subject and creation time")
  void changedProfileKeepsIdentity() {
    User user = provisioned();

    User updated =
        user.withProfile(
            Email.of("new@example.com"),
            com.mosaicglobal.finance.modules.identity.domain.model.DisplayName.of("Ann New"));

    assertThat(updated).isNotSameAs(user);
    assertThat(updated.getId()).isEqualTo(user.getId());
    assertThat(updated.getExternalSubject()).isEqualTo(user.getExternalSubject());
    assertThat(updated.getCreatedAt()).isEqualTo(NOW);
    assertThat(updated.getEmail().value()).isEqualTo("new@example.com");
  }
}
