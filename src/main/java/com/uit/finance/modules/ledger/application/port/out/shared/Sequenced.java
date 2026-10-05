package com.uit.finance.modules.ledger.application.port.out.shared;

import com.uit.finance.shared.kernel.Ensure;

/**
 * Một bản ghi kèm {@code change_seq} của lần ghi gần nhất (ADR-002 §2). Số thứ tự là chuyện của
 * sync, không phải của domain, nên không nằm trong aggregate.
 */
public record Sequenced<T>(T value, long changeSeq) {

  public Sequenced {
    Ensure.notNull(value, "value");
  }
}
