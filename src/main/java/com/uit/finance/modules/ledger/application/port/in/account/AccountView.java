package com.uit.finance.modules.ledger.application.port.in.account;

import com.uit.finance.modules.ledger.domain.model.account.Account;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Bản ghi ví hiện tại trên server. Sync trả kèm bản này trong kết quả từng op (ADR-002 §4). */
public record AccountView(
    UUID id,
    String name,
    String type,
    String currency,
    long openingBalanceMinor,
    int sortOrder,
    @Nullable Instant archivedAt,
    boolean deleted) {

  public static AccountView from(Account account) {
    return new AccountView(
        account.getId().value(),
        account.getDetails().name().value(),
        account.getDetails().type().name(),
        account.currency().getCurrencyCode(),
        account.getDetails().openingBalance().amountMinor(),
        account.getDetails().sortOrder(),
        account.getArchivedAt(),
        account.isDeleted());
  }
}
