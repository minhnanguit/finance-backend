package com.uit.finance.modules.identity.application.service;

import com.uit.finance.modules.identity.application.port.in.CheckUserStatusUseCase;
import com.uit.finance.modules.identity.application.port.out.LoadUserPort;
import com.uit.finance.modules.identity.domain.model.User;
import com.uit.finance.shared.kernel.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class UserStatusService implements CheckUserStatusUseCase {

  private final LoadUserPort loadUser;

  UserStatusService(LoadUserPort loadUser) {
    this.loadUser = loadUser;
  }

  @Override
  public boolean isDeleted(UserId userId) {
    return loadUser.byId(userId).map(User::isDeleted).orElse(false);
  }
}
