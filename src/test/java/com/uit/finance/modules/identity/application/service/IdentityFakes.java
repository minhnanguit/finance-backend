package com.uit.finance.modules.identity.application.service;

import com.uit.finance.modules.identity.application.port.out.IdentityProviderAccountsPort;
import com.uit.finance.modules.identity.application.port.out.IdentityProviderRemovalsPort;
import com.uit.finance.modules.identity.application.port.out.LoadUserPort;
import com.uit.finance.modules.identity.application.port.out.SaveUserPort;
import com.uit.finance.modules.identity.domain.model.ExternalSubject;
import com.uit.finance.modules.identity.domain.model.User;
import com.uit.finance.modules.identity.domain.model.UserStatus;
import com.uit.finance.shared.kernel.DomainEvent;
import com.uit.finance.shared.kernel.DomainEventPublisher;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Fake in-memory cho các port của identity — dùng fake thay mock để đọc được behavior. */
final class IdentityFakes {

  private IdentityFakes() {}

  static final class UserStore implements LoadUserPort, SaveUserPort, IdentityProviderRemovalsPort {

    final Map<UUID, User> byId = new LinkedHashMap<>();

    /** {@code users.idp_removed_at}: cột riêng, không thuộc aggregate. */
    final Map<UUID, Instant> idpRemoved = new LinkedHashMap<>();

    int insertAttempts;
    int updates;

    /** Set để simulate một request khác thắng race insert. */
    User raceWinner;

    @Override
    public Optional<User> byId(UserId id) {
      return Optional.ofNullable(byId.get(id.value()));
    }

    @Override
    public Optional<User> byExternalSubject(ExternalSubject subject) {
      return byId.values().stream()
          .filter(user -> user.getExternalSubject().equals(subject))
          .findFirst();
    }

    @Override
    public boolean insertIfAbsent(User user) {
      insertAttempts++;
      if (raceWinner != null) {
        byId.put(raceWinner.getId().value(), raceWinner);
        return false;
      }
      if (byExternalSubject(user.getExternalSubject()).isPresent()) {
        return false;
      }
      byId.put(user.getId().value(), user);
      return true;
    }

    @Override
    public void update(User user) {
      updates++;
      byId.put(user.getId().value(), user);
    }

    @Override
    public List<User> lockPendingDeletions(Instant requestedBefore, int limit) {
      return byId.values().stream()
          .filter(user -> user.getStatus() == UserStatus.DELETING)
          .filter(user -> user.getDeletionRequestedAt().isBefore(requestedBefore))
          .limit(limit)
          .toList();
    }

    @Override
    public List<PendingRemoval> pending(int limit) {
      return byId.values().stream()
          .filter(User::isDeleted)
          .filter(user -> !idpRemoved.containsKey(user.getId().value()))
          .sorted(Comparator.comparing(User::getDeletionRequestedAt))
          .limit(limit)
          .map(
              user ->
                  new PendingRemoval(
                      user.getId(), user.getExternalSubject(), user.getDeletionRequestedAt()))
          .toList();
    }

    @Override
    public void markRemoved(UserId userId, Instant removedAt) {
      idpRemoved.putIfAbsent(userId.value(), removedAt);
    }
  }

  /** Ghi lại các {@code sub} đã bị xoá trên Keycloak. */
  static final class RecordingIdentityProvider implements IdentityProviderAccountsPort {

    final List<ExternalSubject> deleted = new ArrayList<>();
    boolean enabled = true;

    /** Số lần gọi tiếp theo sẽ lỗi như khi Keycloak không trả lời. */
    int failuresLeft;

    @Override
    public boolean enabled() {
      return enabled;
    }

    @Override
    public void deleteAccount(ExternalSubject subject) {
      if (failuresLeft > 0) {
        failuresLeft--;
        throw new IllegalStateException("Keycloak unavailable");
      }
      deleted.add(subject);
    }
  }

  static final class RecordingEvents implements DomainEventPublisher {

    final List<DomainEvent> published = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
      published.add(event);
    }
  }
}
