package com.mosaicglobal.finance.modules.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mosaicglobal.finance.modules.identity.application.port.in.ProvisionUserUseCase.ProvisionUserCommand;
import com.mosaicglobal.finance.modules.identity.domain.event.UserRegistered;
import com.mosaicglobal.finance.modules.identity.domain.exception.UserProvisioningFailedException;
import com.mosaicglobal.finance.modules.identity.domain.model.Email;
import com.mosaicglobal.finance.modules.identity.domain.model.ExternalSubject;
import com.mosaicglobal.finance.modules.identity.domain.model.User;
import com.mosaicglobal.finance.shared.kernel.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProvisionUserServiceTest {

  private static final String SUBJECT = "0b9c1a2e-keycloak-sub";
  private static final Instant NOW = Instant.parse("2026-09-20T10:15:30Z");

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private IdentityFakes.UserStore users;
  private IdentityFakes.RecordingEvents events;
  private ProvisionUserService service;

  @BeforeEach
  void setUp() {
    users = new IdentityFakes.UserStore();
    events = new IdentityFakes.RecordingEvents();
    service = new ProvisionUserService(users, users, events, clock);
  }

  private static ProvisionUserCommand command(String email, String displayName) {
    return new ProvisionUserCommand(SUBJECT, email, displayName);
  }

  @Test
  @DisplayName("lần đầu thấy sub thì tạo account và publish event")
  void firstSightCreatesAccount() {
    UserId id = service.provision(command("Ann@Example.com ", " Ann "));

    assertThat(users.byId).hasSize(1);
    User created = users.byId.get(id.value());
    assertThat(created.getExternalSubject().value()).isEqualTo(SUBJECT);
    assertThat(created.getEmail().value()).isEqualTo("ann@example.com");
    assertThat(created.getDisplayName().value()).isEqualTo("Ann");
    assertThat(created.getCreatedAt()).isEqualTo(NOW);
    assertThat(events.published).singleElement().isInstanceOf(UserRegistered.class);
  }

  @Test
  @DisplayName("request thứ hai cùng sub thì reuse account, không publish event")
  void secondRequestReusesAccount() {
    UserId first = service.provision(command("ann@example.com", "Ann"));
    events.published.clear();

    UserId second = service.provision(command("ann@example.com", "Ann"));

    assertThat(second).isEqualTo(first);
    assertThat(users.byId).hasSize(1);
    assertThat(users.updates).isZero();
    assertThat(events.published).isEmpty();
  }

  @Test
  @DisplayName("profile đổi trên Keycloak thì được update, id giữ nguyên")
  void profileChangeIsSynced() {
    UserId id = service.provision(command("ann@example.com", "Ann"));

    UserId same = service.provision(command("ann.renamed@example.com", "Ann Renamed"));

    assertThat(same).isEqualTo(id);
    assertThat(users.updates).isEqualTo(1);
    assertThat(users.byId.get(id.value()).getEmail().value()).isEqualTo("ann.renamed@example.com");
    assertThat(users.byId.get(id.value()).getDisplayName().value()).isEqualTo("Ann Renamed");
  }

  @Test
  @DisplayName("thua race insert thì adopt account của bên thắng, không throw")
  void concurrentFirstRequestAdoptsTheWinner() {
    User winner =
        User.provision(
            UserId.newId(),
            ExternalSubject.of(SUBJECT),
            Email.of("ann@example.com"),
            com.mosaicglobal.finance.modules.identity.domain.model.DisplayName.of("Ann"),
            NOW);
    users.raceWinner = winner;

    UserId resolved = service.provision(command("ann@example.com", "Ann"));

    assertThat(resolved).isEqualTo(winner.getId());
    assertThat(users.byId).hasSize(1);
    // Bên thua không được publish lại event mà bên thắng đã publish.
    assertThat(events.published).isEmpty();
  }

  @Test
  @DisplayName("insert không được mà cũng không thấy row nào là invariant bị vỡ")
  void vanishedRowFailsLoudly() {
    users.raceWinner =
        User.provision(
            UserId.newId(),
            ExternalSubject.of("some-other-subject"),
            Email.of("other@example.com"),
            com.mosaicglobal.finance.modules.identity.domain.model.DisplayName.of("Other"),
            NOW);

    assertThatThrownBy(() -> service.provision(command("ann@example.com", "Ann")))
        .isInstanceOf(UserProvisioningFailedException.class);
  }
}
