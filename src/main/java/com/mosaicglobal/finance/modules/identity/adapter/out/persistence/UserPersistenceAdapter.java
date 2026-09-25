package com.mosaicglobal.finance.modules.identity.adapter.out.persistence;

import com.mosaicglobal.finance.modules.identity.application.port.out.LoadUserPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.SaveUserPort;
import com.mosaicglobal.finance.modules.identity.domain.exception.EmailAlreadyRegisteredException;
import com.mosaicglobal.finance.modules.identity.domain.exception.UserNotFoundException;
import com.mosaicglobal.finance.modules.identity.domain.model.ExternalSubject;
import com.mosaicglobal.finance.modules.identity.domain.model.User;
import com.mosaicglobal.finance.shared.kernel.UserId;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class UserPersistenceAdapter implements LoadUserPort, SaveUserPort {

  private final UserJpaRepository repository;

  UserPersistenceAdapter(UserJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<User> byId(UserId id) {
    return repository
        .findById(id.value())
        .filter(entity -> !entity.isDeleted())
        .map(UserPersistenceMapper::toDomain);
  }

  @Override
  public Optional<User> byExternalSubject(ExternalSubject subject) {
    return repository
        .findByExternalSubject(subject.value())
        .filter(entity -> !entity.isDeleted())
        .map(UserPersistenceMapper::toDomain);
  }

  @Override
  public boolean insertIfAbsent(User user) {
    try {
      return repository.insertIfAbsent(
              user.getId().value(),
              user.getExternalSubject().value(),
              user.getEmail().value(),
              user.getDisplayName().value(),
              user.getStatus().name(),
              user.getCreatedAt())
          == 1;
    } catch (DataIntegrityViolationException e) {
      // Không phải index của subject (ON CONFLICT đã xử lý) — vậy là duplicate email.
      throw new EmailAlreadyRegisteredException();
    }
  }

  @Override
  public void update(User user) {
    UserJpaEntity entity =
        repository
            .findById(user.getId().value())
            .orElseThrow(() -> new UserNotFoundException(user.getId()));
    UserPersistenceMapper.applyProfile(entity, user);
    repository.saveAndFlush(entity);
  }
}
