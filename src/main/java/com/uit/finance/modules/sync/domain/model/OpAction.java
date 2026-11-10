package com.uit.finance.modules.sync.domain.model;

/**
 * {@code CREATE} (ADR-008 §3): chỉ ghi khi id chưa có với user. Sync kiểm rồi đổi sang {@code
 * UPSERT} trước khi gọi handler, nên handler không bao giờ thấy {@code CREATE}.
 */
public enum OpAction {
  UPSERT,
  DELETE,
  CREATE
}
