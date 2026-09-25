package com.mosaicglobal.finance.modules.identity.adapter.out.persistence;

import com.mosaicglobal.finance.modules.identity.domain.model.DisplayName;
import com.mosaicglobal.finance.modules.identity.domain.model.Email;
import com.mosaicglobal.finance.modules.identity.domain.model.ExternalSubject;
import com.mosaicglobal.finance.modules.identity.domain.model.User;
import com.mosaicglobal.finance.shared.kernel.UserId;

final class UserPersistenceMapper {

  private UserPersistenceMapper() {}

  static void applyProfile(UserJpaEntity entity, User user) {
    entity.applyProfile(user.getEmail().value(), user.getDisplayName().value(), user.getStatus());
  }

  static User toDomain(UserJpaEntity entity) {
    return User.rehydrate(
        new UserId(entity.getId()),
        new ExternalSubject(entity.getExternalSubject()),
        new Email(entity.getEmail()),
        new DisplayName(entity.getDisplayName()),
        entity.getStatus(),
        entity.getCreatedAt());
  }
}
