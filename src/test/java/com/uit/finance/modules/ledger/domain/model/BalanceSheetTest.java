package com.uit.finance.modules.ledger.domain.model;

import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.ANN;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.archived;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.deleted;
import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.shared.kernel.Money;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BalanceSheetTest {

  private static Account opened(String currency, long opening) {
    return Account.open(
        new AccountId(UUID.randomUUID()),
        ANN,
        AccountDetails.parse("Ví", "BANK", currency, opening, 0));
  }

  @Test
  @DisplayName("ví chưa có giao dịch có số dư bằng số dư ban đầu")
  void accountWithoutFlowsKeepsOpeningBalance() {
    Account wallet = opened("VND", 200_000);

    BalanceSheet sheet = BalanceSheet.of(List.of(wallet), Map.of());

    assertThat(sheet.accounts())
        .singleElement()
        .extracting(BalanceSheet.AccountBalance::balance)
        .isEqualTo(Money.of(200_000, "VND"));
  }

  @Test
  @DisplayName("chuyển 1 triệu từ bank sang ví: tổng không đổi")
  void transferKeepsTotal() {
    Account bank = opened("VND", 5_000_000);
    Account wallet = opened("VND", 0);

    BalanceSheet sheet =
        BalanceSheet.of(
            List.of(bank, wallet),
            Map.of(
                bank.getId(), new AccountFlows(bank.getId(), 0, 0, 1_000_000, 0),
                wallet.getId(), new AccountFlows(wallet.getId(), 0, 0, 0, 1_000_000)));

    assertThat(sheet.accounts())
        .extracting(BalanceSheet.AccountBalance::balance)
        .containsExactly(Money.of(4_000_000, "VND"), Money.of(1_000_000, "VND"));
    assertThat(sheet.totals()).containsExactly(Money.of(5_000_000, "VND"));
  }

  @Test
  @DisplayName("tổng theo từng tiền tệ, bỏ ví archive, không liệt kê ví đã xoá")
  void totalsPerCurrencySkipArchivedAndDeleted() {
    Account vnd = opened("VND", 100_000);
    Account usd = opened("USD", 2_500);
    Account hidden = archived(opened("VND", 900_000));
    Account gone = deleted(opened("VND", 1));

    BalanceSheet sheet = BalanceSheet.of(List.of(vnd, usd, hidden, gone), Map.of());

    assertThat(sheet.accounts()).hasSize(3);
    assertThat(sheet.totals()).containsExactly(Money.of(2_500, "USD"), Money.of(100_000, "VND"));
  }
}
