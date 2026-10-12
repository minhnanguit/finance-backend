package com.uit.finance.modules.ledger.adapter.out.persistence.account;

import com.uit.finance.modules.ledger.adapter.out.persistence.support.SqlValues;
import com.uit.finance.modules.ledger.domain.model.account.Account;
import com.uit.finance.modules.ledger.domain.model.account.AccountDetails;
import com.uit.finance.modules.ledger.domain.model.account.AccountId;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerName;
import com.uit.finance.shared.kernel.Money;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

final class AccountPersistenceMapper {

  private AccountPersistenceMapper() {}

  static Account toDomain(AccountJpaEntity entity) {
    return Account.rehydrate(
        new AccountId(entity.getId()),
        new UserId(entity.getUserId()),
        new AccountDetails(
            new LedgerName(entity.getName()),
            entity.getType(),
            Money.of(entity.getOpeningBalanceMinor(), entity.getCurrency()),
            entity.getSortOrder()),
        entity.getArchivedAt(),
        entity.getDeletedAt());
  }

  static void apply(Account account, AccountJpaEntity entity) {
    AccountDetails details = account.getDetails();
    entity.apply(
        details.name().value(),
        details.type(),
        details.currency().getCurrencyCode(),
        details.openingBalance().amountMinor(),
        details.sortOrder(),
        account.getArchivedAt());
    if (account.getDeletedAt() != null && !entity.isDeleted()) {
      entity.markDeleted(account.getDeletedAt());
    }
  }

  /** Tham số cho {@code AccountPersistenceAdapter.INSERT}. */
  static MapSqlParameterSource insertParams(Account account, long changeSeq, Instant now) {
    AccountDetails details = account.getDetails();
    return new SqlValues()
        .uuid("id", account.getId().value())
        .uuid("userId", account.getOwner().value())
        .text("name", details.name().value())
        .text("type", details.type().name())
        .text("currency", details.currency().getCurrencyCode())
        .number("openingBalanceMinor", details.openingBalance().amountMinor())
        .number("sortOrder", details.sortOrder())
        .timestamp("archivedAt", account.getArchivedAt())
        .number("changeSeq", changeSeq)
        .timestamp("now", now)
        .timestamp("deletedAt", account.getDeletedAt())
        .build();
  }
}
