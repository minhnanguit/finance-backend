package com.uit.finance.modules.ledger.application.port.in;

import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.UUID;

/**
 * Số dư từng ví và tổng theo từng tiền tệ, tính từ lịch sử (ADR-005 §5). Chỉ tính giao dịch {@code
 * CONFIRMED} chưa xoá; tổng bỏ qua ví đã archive.
 */
public interface GetBalancesUseCase {

  Balances balances(UserId userId);

  record Balances(List<AccountBalance> accounts, List<CurrencyTotal> totals) {

    public Balances {
      accounts = List.copyOf(accounts);
      totals = List.copyOf(totals);
    }
  }

  record AccountBalance(UUID accountId, long balanceMinor, String currency, boolean archived) {}

  record CurrencyTotal(String currency, long totalMinor) {}
}
