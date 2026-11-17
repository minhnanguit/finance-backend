package com.uit.finance.modules.identity.domain.model;

/**
 * Vòng đời của một tài khoản (ADR-008).
 *
 * <pre>
 *   ACTIVE ──xoá tài khoản──▶ DELETING ──hết thời gian chờ──▶ DELETED
 * </pre>
 *
 * Từ {@code DELETING} trở đi tài khoản không dùng được nữa: mọi request trả {@code 403
 * identity.user_deleted}. Dòng {@code users} được giữ lại (đã xoá thông tin cá nhân) để token còn
 * hạn không tạo lại được tài khoản qua JIT provisioning.
 */
public enum UserStatus {
  ACTIVE,
  DISABLED,
  /**
   * Đã yêu cầu xoá: tài khoản Keycloak bị xoá ngay, dữ liệu của các module chờ hết thời gian chờ.
   */
  DELETING,
  /** Đã phát {@code identity.user.deleted}: các module xoá thật dữ liệu của user. */
  DELETED;

  public boolean isDeleted() {
    return this == DELETING || this == DELETED;
  }
}
