package com.mosaicglobal.finance.modules.identity.adapter.in.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mosaicglobal.finance.modules.identity.adapter.config.IdentityProperties;
import com.mosaicglobal.finance.modules.identity.application.port.in.ProvisionUserUseCase;
import com.mosaicglobal.finance.modules.identity.application.port.in.ProvisionUserUseCase.ProvisionUserCommand;
import com.mosaicglobal.finance.shared.kernel.UserId;
import com.mosaicglobal.finance.shared.security.CurrentUserResolver;
import com.mosaicglobal.finance.shared.security.SubjectClaims;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Inbound adapter: plugs {@code identity} into the security layer's
 * {@link CurrentUserResolver}
 * SPI.
 *
 * <p>
 * Sits on the hot path — every authenticated request goes through it — so a hit
 * costs one
 * in-memory lookup and no database round trip. Only a miss reaches the use
 * case, which is also
 * where a profile change from the provider is picked up; the TTL is therefore
 * the staleness bound.
 *
 * <p>
 * {@link Cache#get(Object, java.util.function.Function)} collapses concurrent
 * misses for the
 * same subject into a single call, so a burst of first requests provisions
 * once.
 */
@Component
class ProvisioningCurrentUserResolver implements CurrentUserResolver {

    private final ProvisionUserUseCase provisionUser;
    private final Cache<String, UUID> resolvedSubjects;

    ProvisioningCurrentUserResolver(
            ProvisionUserUseCase provisionUser, IdentityProperties properties) {
        this.provisionUser = provisionUser;
        this.resolvedSubjects = Caffeine.newBuilder()
                .maximumSize(properties.subjectCacheSize())
                .expireAfterWrite(properties.subjectCacheTtl())
                .build();
    }

    @Override
    public UserId resolve(SubjectClaims claims) {
        UUID userId = resolvedSubjects.get(
                claims.subject(),
                subject -> provisionUser
                        .provision(
                                new ProvisionUserCommand(subject, claims.email(), claims.displayName()))
                        .value());
        return new UserId(userId);
    }
}
