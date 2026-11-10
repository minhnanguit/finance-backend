package com.uit.finance.modules.ledger.application.port.in.reference;

import com.uit.finance.modules.ledger.domain.model.account.Account;
import java.util.UUID;

/**
 * Trạng thái hiện tại của một ví, đủ để module khác kiểm tham chiếu (ADR-008 §8). Không có tên, số
 * dư hay số dư đầu (ADR-006 B8).
 *
 * @param currency mã ISO-4217 của ví
 */
public record AccountRef(UUID id, String currency, boolean archived, boolean deleted) {

  public static AccountRef from(Account account) {
    return new AccountRef(
        account.getId().value(),
        account.getDetails().currency().getCurrencyCode(),
        account.isArchived(),
        account.isDeleted());
  }
}
