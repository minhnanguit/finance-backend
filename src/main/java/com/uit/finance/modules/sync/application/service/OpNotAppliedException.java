package com.uit.finance.modules.sync.application.service;

import com.uit.finance.modules.sync.domain.model.OpVerdict;

/**
 * Ném ra khỏi {@link ApplyOpService} để rollback mọi thay đổi của một op không được áp, kể cả số
 * {@code change_seq} đã lấy (ADR-002 §7). Không phải lỗi; không cần stack trace.
 */
final class OpNotAppliedException extends RuntimeException {

  private final transient OpVerdict verdict;

  OpNotAppliedException(OpVerdict verdict) {
    super(verdict.outcome().name(), null, false, false);
    this.verdict = verdict;
  }

  OpVerdict verdict() {
    return verdict;
  }
}
