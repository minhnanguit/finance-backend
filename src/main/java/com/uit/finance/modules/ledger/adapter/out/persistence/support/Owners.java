package com.uit.finance.modules.ledger.adapter.out.persistence.support;

import com.uit.finance.shared.kernel.UserId;

/** Hợp đồng của port out: {@code owner} truyền vào phải đúng là chủ của bản ghi (ADR-006 B1). */
public final class Owners {

  private Owners() {}

  public static void require(UserId owner, UserId recordOwner) {
    if (!owner.equals(recordOwner)) {
      throw new IllegalArgumentException("owner does not match the record being written");
    }
  }
}
