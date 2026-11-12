package com.uit.finance.modules.identity.adapter.in.web;

import com.uit.finance.api.v1.MeApi;
import com.uit.finance.api.v1.model.AccountDeletionStatus;
import com.uit.finance.api.v1.model.UserProfile;
import com.uit.finance.modules.identity.application.port.in.GetUserProfileUseCase;
import com.uit.finance.modules.identity.application.port.in.RequestUserDeletionUseCase;
import com.uit.finance.modules.identity.application.port.in.RequestUserDeletionUseCase.RequestUserDeletionCommand;
import com.uit.finance.shared.security.AuthenticatedUser;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController implements MeApi {

  private final GetUserProfileUseCase userProfile;
  private final RequestUserDeletionUseCase deleteAccount;
  private final UserWebMapper mapper;

  MeController(
      GetUserProfileUseCase userProfile,
      RequestUserDeletionUseCase deleteAccount,
      UserWebMapper mapper) {
    this.userProfile = userProfile;
    this.deleteAccount = deleteAccount;
    this.mapper = mapper;
  }

  @Override
  public ResponseEntity<UserProfile> getCurrentUser() {
    return ResponseEntity.ok(
        mapper.toProfile(userProfile.profile(AuthenticatedUser.requireCurrent())));
  }

  /** {@code auth_time} lấy từ token: chỉ token vừa đăng nhập lại mới xoá được (ADR-008 A2). */
  @Override
  public ResponseEntity<AccountDeletionStatus> deleteCurrentUser() {
    Instant requestedAt =
        deleteAccount.requestDeletion(
            new RequestUserDeletionCommand(
                AuthenticatedUser.requireCurrent(),
                AuthenticatedUser.authenticationTime().orElse(null)));
    return ResponseEntity.accepted().body(mapper.toDeletionStatus(requestedAt));
  }
}
