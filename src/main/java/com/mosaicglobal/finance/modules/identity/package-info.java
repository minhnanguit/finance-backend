/**
 * Identity: local record của từng user và mapping {@code sub} Keycloak → internal id.
 *
 * <p>Không giữ password, session hay refresh token — Keycloak lo (ADR-004). Module khác biết về
 * user qua event trong {@code domain.event}, không bao giờ đọc table của identity.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Identity",
    allowedDependencies = {})
package com.mosaicglobal.finance.modules.identity;
