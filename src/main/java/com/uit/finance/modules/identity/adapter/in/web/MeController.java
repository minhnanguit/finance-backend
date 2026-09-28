package com.uit.finance.modules.identity.adapter.in.web;

import com.uit.finance.api.v1.MeApi;
import com.uit.finance.api.v1.model.UserProfile;
import com.uit.finance.modules.identity.application.port.in.GetUserProfileUseCase;
import com.uit.finance.shared.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController implements MeApi {

  private final GetUserProfileUseCase userProfile;
  private final UserWebMapper mapper;

  MeController(GetUserProfileUseCase userProfile, UserWebMapper mapper) {
    this.userProfile = userProfile;
    this.mapper = mapper;
  }

  @Override
  public ResponseEntity<UserProfile> getCurrentUser() {
    return ResponseEntity.ok(
        mapper.toProfile(userProfile.profile(AuthenticatedUser.requireCurrent())));
  }
}
