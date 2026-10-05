package com.uit.finance.modules.sync.domain.model;

/** Kết quả của một op (ADR-002 §4). Mọi kết quả trừ {@code RETRY} là chốt. */
public enum OpOutcome {
  APPLIED,
  DUPLICATE,
  CONFLICT,
  REJECTED,
  RETRY;

  public boolean isFinal() {
    return this != RETRY;
  }
}
