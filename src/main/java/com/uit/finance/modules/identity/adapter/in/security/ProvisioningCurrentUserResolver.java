package com.uit.finance.modules.identity.adapter.in.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uit.finance.modules.identity.adapter.config.IdentityProperties;
import com.uit.finance.modules.identity.application.port.in.ProvisionUserUseCase;
import com.uit.finance.modules.identity.application.port.in.ProvisionUserUseCase.ProvisionUserCommand;
import com.uit.finance.shared.kernel.UserId;
import com.uit.finance.shared.security.CurrentUserResolver;
import com.uit.finance.shared.security.SubjectClaims;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Plug {@code identity} vào SPI {@link CurrentUserResolver} của security layer.
 *
 * <p>Nằm trên hot path — mọi request có token đều qua đây — nên cache hit chỉ tốn một lần lookup
 * in-memory. {@link Cache#get(Object, java.util.function.Function)} gộp các concurrent miss của
 * cùng một {@code sub} thành một lần gọi, nên burst request đầu tiên chỉ provision một lần.
 */
@Component
class ProvisioningCurrentUserResolver implements CurrentUserResolver {

  private final ProvisionUserUseCase provisionUser;
  private final Cache<String, UUID> resolvedSubjects;

  ProvisioningCurrentUserResolver(
      ProvisionUserUseCase provisionUser, IdentityProperties properties) {
    this.provisionUser = provisionUser;
    this.resolvedSubjects =
        Caffeine.newBuilder()
            .maximumSize(properties.subjectCacheSize())
            .expireAfterWrite(properties.subjectCacheTtl())
            .build();
  }

  @Override
  public UserId resolve(SubjectClaims claims) {
    UUID userId =
        resolvedSubjects.get(
            claims.subject(),
            subject ->
                provisionUser
                    .provision(
                        new ProvisionUserCommand(subject, claims.email(), claims.displayName()))
                    .value());
    return new UserId(userId);
  }
}
