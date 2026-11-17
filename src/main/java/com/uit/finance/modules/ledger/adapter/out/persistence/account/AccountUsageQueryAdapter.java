package com.uit.finance.modules.ledger.adapter.out.persistence.account;

import com.uit.finance.modules.ledger.application.port.out.account.AccountUsagePort;
import com.uit.finance.modules.ledger.domain.model.account.AccountId;
import com.uit.finance.shared.kernel.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Đếm và kiểm tra bằng SQL, không load entity. */
@Component
class AccountUsageQueryAdapter implements AccountUsagePort {

  private final JdbcClient jdbc;

  AccountUsageQueryAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public long countAccounts(UserId owner) {
    return jdbc.sql("SELECT count(*) FROM accounts WHERE user_id = :owner AND deleted_at IS NULL")
        .param("owner", owner.value())
        .query(Long.class)
        .single();
  }

  /** Dùng 2 index {@code account_id} và {@code counter_account_id} (BitmapOr), dừng ở dòng đầu. */
  @Override
  public boolean isReferenced(UserId owner, AccountId id) {
    return jdbc.sql(
            """
            SELECT EXISTS (
              SELECT 1 FROM transactions
              WHERE user_id = :owner AND deleted_at IS NULL
                AND (account_id = :id OR counter_account_id = :id))
            """)
        .param("owner", owner.value())
        .param("id", id.value())
        .query(Boolean.class)
        .single();
  }
}
