package com.uit.finance.modules.ledger.domain.model.shared;

/** Loại bản ghi của ledger. {@link #wireName()} là tên entity trên contract sync (ADR-002 §4). */
public enum LedgerEntity {
  ACCOUNT("account"),
  CATEGORY("category"),
  TRANSACTION("transaction");

  private final String wireName;

  LedgerEntity(String wireName) {
    this.wireName = wireName;
  }

  public String wireName() {
    return wireName;
  }
}
