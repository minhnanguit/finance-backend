package com.uit.finance.modules.identity.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.finance.modules.identity.application.port.in.RequestUserDeletionUseCase.RequestUserDeletionCommand;
import com.uit.finance.modules.identity.domain.event.UserDeleted;
import com.uit.finance.modules.identity.domain.event.UserDeletionRequested;
import com.uit.finance.modules.identity.domain.exception.ReauthenticationRequiredException;
import com.uit.finance.modules.identity.domain.exception.UserDeletedException;
import com.uit.finance.modules.identity.domain.model.Email;
import com.uit.finance.modules.identity.domain.model.ExternalSubject;
import com.uit.finance.modules.identity.domain.model.User;
import com.uit.finance.modules.identity.domain.model.UserStatus;
import com.uit.finance.shared.kernel.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Use case xoá tài khoản (ADR-008 A1–A8) với fake port. Luồng thật qua HTTP + RabbitMQ + job: IT.
 */
class UserDeletionServicesTest {

  private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private IdentityFakes.UserStore users;
  private IdentityFakes.RecordingEvents events;

  @BeforeEach
  void setUp() {
    users = new IdentityFakes.UserStore();
    events = new IdentityFakes.RecordingEvents();
  }

  private User active(String subject) {
    User user =
        User.provision(
            UserId.newId(),
            ExternalSubject.of(subject),
            Email.of(subject + "@example.com"),
            com.uit.finance.modules.identity.domain.model.DisplayName.of("Ann"),
            NOW.minus(Duration.ofDays(30)));
    user.pullDomainEvents();
    users.byId.put(user.getId().value(), user);
    return user;
  }

  @Nested
  class RequestDeletion {

    private RequestUserDeletionService service;

    @BeforeEach
    void setUp() {
      service = new RequestUserDeletionService(users, users, events, clock);
    }

    @Test
    @DisplayName("vừa đăng nhập lại: DELETING, xoá thông tin cá nhân, ghi event vào outbox")
    void recentLoginDeletes() {
      User ann = active("sub-ann");

      Instant requestedAt =
          service.requestDeletion(
              new RequestUserDeletionCommand(ann.getId(), NOW.minusSeconds(30)));

      assertThat(requestedAt).isEqualTo(NOW);
      User stored = users.byId.get(ann.getId().value());
      assertThat(stored.getStatus()).isEqualTo(UserStatus.DELETING);
      assertThat(stored.getEmail().value()).endsWith("@erased.invalid");
      assertThat(stored.getDeletionRequestedAt()).isEqualTo(NOW);
      assertThat(events.published).singleElement().isInstanceOf(UserDeletionRequested.class);
    }

    @Test
    @DisplayName(
        "token không có auth_time, hoặc đăng nhập quá 5 phút: reauth_required, không đổi gì")
    void staleOrMissingLoginIsRefused() {
      User ann = active("sub-ann");

      for (Instant authTime :
          new Instant[] {null, NOW.minus(Duration.ofMinutes(5)).minusSeconds(1)}) {
        assertThatThrownBy(
                () ->
                    service.requestDeletion(new RequestUserDeletionCommand(ann.getId(), authTime)))
            .isInstanceOf(ReauthenticationRequiredException.class);
      }
      assertThat(users.byId.get(ann.getId().value()).isActive()).isTrue();
      assertThat(events.published).isEmpty();
    }

    @Test
    @DisplayName("auth_time ở tương lai quá độ lệch đồng hồ cho phép bị từ chối; lệch ít thì nhận")
    void clockSkew() {
      User ann = active("sub-ann");
      User bob = active("sub-bob");

      assertThatThrownBy(
              () ->
                  service.requestDeletion(
                      new RequestUserDeletionCommand(ann.getId(), NOW.plus(Duration.ofMinutes(2)))))
          .isInstanceOf(ReauthenticationRequiredException.class);
      service.requestDeletion(new RequestUserDeletionCommand(bob.getId(), NOW.plusSeconds(20)));

      assertThat(users.byId.get(bob.getId().value()).isDeleted()).isTrue();
    }

    @Test
    @DisplayName("xoá lần hai: user_deleted (app coi như xong)")
    void secondRequestReportsDeleted() {
      User ann = active("sub-ann");
      service.requestDeletion(new RequestUserDeletionCommand(ann.getId(), NOW));

      assertThatThrownBy(
              () -> service.requestDeletion(new RequestUserDeletionCommand(ann.getId(), NOW)))
          .isInstanceOf(UserDeletedException.class);
    }
  }

  @Nested
  class FinalizeDeletions {

    private FinalizeUserDeletionsService service;

    @BeforeEach
    void setUp() {
      service =
          new FinalizeUserDeletionsService(
              new FinalizeUserDeletionBatchService(users, users, events, clock));
    }

    private User deleting(String subject, Instant requestedAt) {
      User user = active(subject).requestDeletion(requestedAt);
      user.pullDomainEvents();
      users.byId.put(user.getId().value(), user);
      return user;
    }

    @Test
    @DisplayName("quá thời gian chờ: DELETED + UserDeleted; chưa tới hạn và còn dùng: giữ nguyên")
    void onlyDueDeletionsAreFinalized() {
      User due = deleting("sub-due", NOW.minus(Duration.ofMinutes(11)));
      User notYet = deleting("sub-new", NOW.minus(Duration.ofMinutes(5)));
      User ann = active("sub-ann");

      int done = service.finalizeRequestedBefore(NOW.minus(Duration.ofMinutes(10)));

      assertThat(done).isEqualTo(1);
      assertThat(users.byId.get(due.getId().value()).getStatus()).isEqualTo(UserStatus.DELETED);
      assertThat(users.byId.get(notYet.getId().value()).getStatus()).isEqualTo(UserStatus.DELETING);
      assertThat(users.byId.get(ann.getId().value()).isActive()).isTrue();
      assertThat(events.published)
          .singleElement()
          .isInstanceOfSatisfying(
              UserDeleted.class, e -> assertThat(e.userId()).isEqualTo(due.getId().value()));
    }

    @Test
    @DisplayName("nhiều hơn một lô: chạy tới khi hết, không bỏ sót")
    void processesEveryBatch() {
      int count = FinalizeUserDeletionsService.BATCH_SIZE + 3;
      IntStream.range(0, count).forEach(i -> deleting("sub-" + i, NOW.minus(Duration.ofHours(1))));

      int done = service.finalizeRequestedBefore(NOW);

      assertThat(done).isEqualTo(count);
      assertThat(users.byId.values()).allMatch(user -> user.getStatus() == UserStatus.DELETED);
      assertThat(events.published).hasSize(count);
    }
  }

  @Nested
  class RemoveIdentityProviderAccounts {

    private IdentityFakes.RecordingIdentityProvider identityProvider;
    private RemoveIdentityProviderAccountsService service;

    @BeforeEach
    void setUp() {
      identityProvider = new IdentityFakes.RecordingIdentityProvider();
      service = new RemoveIdentityProviderAccountsService(users, identityProvider, clock);
    }

    private User deleting(String subject) {
      User deleting = active(subject).requestDeletion(NOW.minusSeconds(5));
      users.byId.put(deleting.getId().value(), deleting);
      return deleting;
    }

    @Test
    @DisplayName(
        "tài khoản đang xoá: xoá trên Keycloak theo sub rồi đánh dấu; tài khoản còn dùng thì không")
    void removesOnlyDeletedAccountsOnce() {
      User ann = deleting("sub-ann");
      active("sub-bob");

      assertThat(service.removePending()).isEqualTo(1);
      assertThat(identityProvider.deleted).containsExactly(ExternalSubject.of("sub-ann"));
      assertThat(users.idpRemoved).containsOnlyKeys(ann.getId().value());

      assertThat(service.removePending()).isZero();
      assertThat(identityProvider.deleted).hasSize(1);
    }

    @Test
    @DisplayName(
        "Keycloak lỗi: dừng lô, không đánh dấu gì; lần chạy sau làm tiếp — không bao giờ bỏ cuộc")
    void keepsRetryingUntilKeycloakIsBack() {
      deleting("sub-ann");
      deleting("sub-bob");
      identityProvider.failuresLeft = 1;

      assertThat(service.removePending()).isZero();
      assertThat(users.idpRemoved).isEmpty();
      assertThat(identityProvider.deleted).isEmpty();

      assertThat(service.removePending()).isEqualTo(2);
      assertThat(users.idpRemoved).hasSize(2);
    }

    @Test
    @DisplayName("client bị tắt: không gọi, không đánh dấu; bật lại thì làm bù")
    void disabledClientKeepsTheWorkPending() {
      deleting("sub-ann");
      identityProvider.enabled = false;

      assertThat(service.removePending()).isZero();
      assertThat(identityProvider.deleted).isEmpty();
      assertThat(users.idpRemoved).isEmpty();

      identityProvider.enabled = true;
      assertThat(service.removePending()).isEqualTo(1);
    }
  }
}
