package com.uit.finance.modules.identity.application.port.in;

import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;

public interface GetUserProfileUseCase {

  UserProfile profile(UserId userId);

  record UserProfile(UserId id, String email, String displayName, Instant createdAt) {}
}
