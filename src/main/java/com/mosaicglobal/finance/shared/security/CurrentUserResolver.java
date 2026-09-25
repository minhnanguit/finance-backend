package com.mosaicglobal.finance.shared.security;

import com.mosaicglobal.finance.shared.kernel.UserId;

/**
 * Maps an authenticated IdP subject to the id this system uses everywhere else.
 *
 * <p>Dependency inversion: the security layer needs the mapping but must not know how users are
 * stored, so it owns this interface and {@code modules.identity} provides the implementation.
 *
 * <p>Called once per request, so implementations are expected to cache. Implementations must be
 * idempotent: two concurrent first-ever requests for the same subject must yield the same {@link
 * UserId}, not two accounts.
 */
public interface CurrentUserResolver {

  UserId resolve(SubjectClaims claims);
}
