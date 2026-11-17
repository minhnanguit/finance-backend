package com.uit.finance.modules.ledger.adapter.out.persistence.account;

import com.uit.finance.modules.ledger.application.port.out.account.LoadAccountFlowsPort;
import com.uit.finance.modules.ledger.domain.model.account.AccountFlows;
import com.uit.finance.modules.ledger.domain.model.account.AccountId;
import com.uit.finance.modules.ledger.domain.model.transaction.TransactionStatus;
import com.uit.finance.modules.ledger.domain.model.transaction.TransactionType;
import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Tổng tiền qua từng ví bằng một câu SQL (ADR-005 §5). Chuyển tiền được đếm 2 lần: chiều đi ở ví
 * nguồn, chiều đến ở ví đích (nhánh {@code UNION ALL} thứ hai).
 */
@Component
class AccountFlowsQueryAdapter implements LoadAccountFlowsPort {

  private static final String FLOWS =
      """
      SELECT account_id,
             COALESCE(SUM(amount_minor) FILTER (WHERE flow = :income), 0)     AS income,
             COALESCE(SUM(amount_minor) FILTER (WHERE flow = :expense), 0)    AS expense,
             COALESCE(SUM(amount_minor) FILTER (WHERE flow = :transfer), 0)   AS transfer_out,
             COALESCE(SUM(amount_minor) FILTER (WHERE flow = 'TRANSFER_IN'), 0) AS transfer_in
      FROM (
        SELECT account_id, type AS flow, amount_minor
        FROM transactions
        WHERE user_id = :owner AND status = :confirmed AND deleted_at IS NULL
        UNION ALL
        SELECT counter_account_id, 'TRANSFER_IN', amount_minor
        FROM transactions
        WHERE user_id = :owner AND status = :confirmed AND deleted_at IS NULL AND type = :transfer
      ) flows
      GROUP BY account_id
      """;

  private final JdbcClient jdbc;

  AccountFlowsQueryAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<AccountFlows> flowsByAccount(UserId owner) {
    return jdbc.sql(FLOWS)
        .param("owner", owner.value())
        .param("confirmed", TransactionStatus.CONFIRMED.name())
        .param("income", TransactionType.INCOME.name())
        .param("expense", TransactionType.EXPENSE.name())
        .param("transfer", TransactionType.TRANSFER.name())
        .query(
            (rs, row) ->
                new AccountFlows(
                    new AccountId(rs.getObject("account_id", UUID.class)),
                    rs.getLong("income"),
                    rs.getLong("expense"),
                    rs.getLong("transfer_out"),
                    rs.getLong("transfer_in")))
        .list();
  }
}
