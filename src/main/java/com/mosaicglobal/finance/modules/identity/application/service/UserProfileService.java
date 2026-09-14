package com.mosaicglobal.finance.modules.identity.application.service;

import com.mosaicglobal.finance.modules.identity.application.port.in.GetUserProfileUseCase;
import com.mosaicglobal.finance.modules.identity.application.port.out.LoadUserPort;
import com.mosaicglobal.finance.modules.identity.domain.exception.UserNotFoundException;
import com.mosaicglobal.finance.shared.kernel.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class UserProfileService implements GetUserProfileUseCase {

  private final LoadUserPort loadUser;

  UserProfileService(LoadUserPort loadUser) {
    this.loadUser = loadUser;
  }

  @Override
  public UserProfile profile(UserId userId) {
    return loadUser
        .byId(userId)
        .map(
            user ->
                new UserProfile(
                    user.getId(),
                    user.getEmail().value(),
                    user.getDisplayName().value(),
                    user.getCreatedAt()))
        .orElseThrow(() -> new UserNotFoundException(userId));
  }
}
