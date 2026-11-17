package com.uit.finance.modules.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.finance.modules.identity.domain.event.UserDeleted;
import com.uit.finance.modules.identity.domain.event.UserDeletionRequested;
import com.uit.finance.modules.identity.domain.event.UserRegistered;
import com.uit.finance.modules.identity.domain.exception.UserDeletedException;
import com.uit.finance.shared.kernel.UserId;
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
        com.uit.finance.modules.identity.domain.model.DisplayName.of("Ann"),
        NOW);
  }

  @Test
  @DisplayName("provision register đúng một event UserRegistered")
  void provisioningRecordsEvent() {
    User user = provisioned();

    assertThat(user.pullDomainEvents()).singleElement().isInstanceOf(UserRegistered.class);
    assertThat(user.pullDomainEvents()).isEmpty();
  }

  @Test
  @DisplayName("rehydrate từ DB không register event")
  void rehydrateIsSilent() {
    User user =
        User.rehydrate(
            UserId.newId(),
            ExternalSubject.of("keycloak-sub-1"),
            Email.of("ann@example.com"),
            com.uit.finance.modules.identity.domain.model.DisplayName.of("Ann"),
            UserStatus.ACTIVE,
            NOW,
            null);

    assertThat(user.pullDomainEvents()).isEmpty();
    assertThat(user.isActive()).isTrue();
  }

  @Test
  @DisplayName(
      "yêu cầu xoá: xoá thông tin cá nhân, giữ sub, chuyển DELETING, register đúng một event")
  void requestDeletionErasesPersonalData() {
    User user = provisioned();
    user.pullDomainEvents();
    Instant later = NOW.plusSeconds(60);

    User deleting = user.requestDeletion(later);

    assertThat(deleting.getStatus()).isEqualTo(UserStatus.DELETING);
    assertThat(deleting.isDeleted()).isTrue();
    assertThat(deleting.getExternalSubject()).isEqualTo(user.getExternalSubject());
    assertThat(deleting.getEmail().value())
        .isEqualTo("deleted-" + user.getId().value() + "@erased.invalid")
        .doesNotContain("ann");
    assertThat(deleting.getDisplayName())
        .isEqualTo(com.uit.finance.modules.identity.domain.model.DisplayName.ERASED);
    assertThat(deleting.getDeletionRequestedAt()).isEqualTo(later);
    assertThat(deleting.pullDomainEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            UserDeletionRequested.class,
            event -> assertThat(event.userId()).isEqualTo(user.getId().value()));
  }

  @Test
  @DisplayName("tài khoản đã xoá: không xoá lần hai, không nhận profile mới từ token còn hạn")
  void deletedUserRejectsFurtherChanges() {
    User deleting = provisioned().requestDeletion(NOW);

    assertThatThrownBy(() -> deleting.requestDeletion(NOW))
        .isInstanceOf(UserDeletedException.class);
    assertThatThrownBy(
            () ->
                deleting.withProfile(
                    Email.of("ann@example.com"),
                    com.uit.finance.modules.identity.domain.model.DisplayName.of("Ann")))
        .isInstanceOf(UserDeletedException.class);
  }

  @Test
  @DisplayName("hoàn tất xoá chỉ từ DELETING và register UserDeleted")
  void completeDeletionOnlyFromDeleting() {
    User deleting = provisioned().requestDeletion(NOW);
    deleting.pullDomainEvents();

    User deleted = deleting.completeDeletion(NOW.plusSeconds(600));

    assertThat(deleted.getStatus()).isEqualTo(UserStatus.DELETED);
    assertThat(deleted.getDeletionRequestedAt()).isEqualTo(NOW);
    assertThat(deleted.pullDomainEvents()).singleElement().isInstanceOf(UserDeleted.class);
    assertThatThrownBy(() -> provisioned().completeDeletion(NOW))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("profile không đổi thì trả về chính instance đó để skip write")
  void unchangedProfileReturnsSameInstance() {
    User user = provisioned();

    User same =
        user.withProfile(
            Email.of("ann@example.com"),
            com.uit.finance.modules.identity.domain.model.DisplayName.of("Ann"));

    assertThat(same).isSameAs(user);
  }

  @Test
  @DisplayName("profile đổi thì ra instance mới, giữ nguyên id, sub và createdAt")
  void changedProfileKeepsIdentity() {
    User user = provisioned();

    User updated =
        user.withProfile(
            Email.of("new@example.com"),
            com.uit.finance.modules.identity.domain.model.DisplayName.of("Ann New"));

    assertThat(updated).isNotSameAs(user);
    assertThat(updated.getId()).isEqualTo(user.getId());
    assertThat(updated.getExternalSubject()).isEqualTo(user.getExternalSubject());
    assertThat(updated.getCreatedAt()).isEqualTo(NOW);
    assertThat(updated.getEmail().value()).isEqualTo("new@example.com");
  }
}
