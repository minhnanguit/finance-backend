package com.uit.finance.modules.ledger.domain.model;

import org.jspecify.annotations.Nullable;

/** {@code DRAFT} có sẵn từ đầu để Review Inbox, OCR chỉ cần tạo nháp (ADR-005 D6). */
public enum TransactionStatus {
  CONFIRMED,
  DRAFT;

  public static TransactionStatus parse(@Nullable String raw) {
    return Fields.enumValue(TransactionStatus.class, raw, "status");
  }
}
