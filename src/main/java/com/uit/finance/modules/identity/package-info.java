/**
 * Identity: local record của từng user và mapping {@code sub} Keycloak → internal id.
 *
 * <p>Không giữ password, session hay refresh token — Keycloak lo (ADR-004). Module khác biết về
 * user qua event trong {@code domain.event}, không bao giờ đọc table của identity.
 *
 * <p>Xoá tài khoản (ADR-008): {@code DELETE /me} → {@code DELETING} (job xoá tài khoản Keycloak
 * trong vòng {@code idp-removal-interval}, thử lại tới khi xong) → hết thời gian chờ → {@code
 * identity.user.deleted} (mỗi module tự xoá dữ liệu của mình).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Identity",
    allowedDependencies = {})
package com.uit.finance.modules.identity;
