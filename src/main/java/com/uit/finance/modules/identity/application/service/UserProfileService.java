package com.uit.finance.modules.identity.application.service;

import com.uit.finance.modules.identity.application.port.in.GetUserProfileUseCase;
import com.uit.finance.modules.identity.application.port.out.LoadUserPort;
import com.uit.finance.modules.identity.domain.exception.UserNotFoundException;
import com.uit.finance.modules.identity.domain.model.User;
import com.uit.finance.shared.kernel.UserId;
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
    User user = loadUser.byId(userId).orElseThrow(() -> new UserNotFoundException(userId));
    // Instance khác còn giữ cache "chưa xoá" vài giây: vẫn không trả profile của tài khoản đã xoá.
    user.requireNotDeleted();
    return new UserProfile(
        user.getId(), user.getEmail().value(), user.getDisplayName().value(), user.getCreatedAt());
  }
}
