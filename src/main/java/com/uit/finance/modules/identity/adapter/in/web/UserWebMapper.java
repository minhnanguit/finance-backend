package com.uit.finance.modules.identity.adapter.in.web;

import com.uit.finance.modules.identity.application.port.in.GetUserProfileUseCase;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
class UserWebMapper {

  com.uit.finance.api.v1.model.UserProfile toProfile(
      GetUserProfileUseCase.UserProfile profile) {
    return new com.uit.finance.api.v1.model.UserProfile()
        .id(profile.id().value())
        .email(profile.email())
        .displayName(profile.displayName())
        .createdAt(profile.createdAt().atOffset(ZoneOffset.UTC));
  }
}
