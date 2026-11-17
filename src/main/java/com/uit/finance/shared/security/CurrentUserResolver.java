package com.uit.finance.shared.security;

import com.uit.finance.shared.kernel.UserId;

/**
 * Map {@code sub} của Keycloak thành internal {@link UserId}.
 *
 * <p>Security layer cần mapping này nhưng không được biết user lưu ở đâu, nên interface nằm ở đây
 * còn {@code modules.identity} implement (dependency inversion).
 *
 * <p>Được gọi ở mọi request nên implementation phải có cache, và phải idempotent: hai concurrent
 * request đầu tiên cho cùng một {@code sub} phải ra cùng một {@link UserId}.
 */
public interface CurrentUserResolver {

  UserId resolve(SubjectClaims claims);
}
