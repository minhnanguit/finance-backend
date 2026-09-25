package com.mosaicglobal.finance.modules.identity.application.service;

import com.mosaicglobal.finance.modules.identity.application.port.out.LoadUserPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.SaveUserPort;
import com.mosaicglobal.finance.modules.identity.domain.model.ExternalSubject;
import com.mosaicglobal.finance.modules.identity.domain.model.User;
import com.mosaicglobal.finance.shared.kernel.DomainEvent;
import com.mosaicglobal.finance.shared.kernel.DomainEventPublisher;
import com.mosaicglobal.finance.shared.kernel.UserId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Fake in-memory cho các port của identity — dùng fake thay mock để đọc được behavior. */
final class IdentityFakes {

  private IdentityFakes() {}

  static final class UserStore implements LoadUserPort, SaveUserPort {

    final Map<UUID, User> byId = new LinkedHashMap<>();
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
  }

  static final class RecordingEvents implements DomainEventPublisher {

    final List<DomainEvent> published = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
      published.add(event);
    }
  }
}
