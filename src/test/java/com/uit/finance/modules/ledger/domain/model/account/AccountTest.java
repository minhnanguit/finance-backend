package com.uit.finance.modules.ledger.domain.model.account;

import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.ANN;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.NOW;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.account;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.accountDetails;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.modules.ledger.domain.exception.CurrencyLockedException;
import com.uit.finance.modules.ledger.domain.exception.EntityDeletedException;
import com.uit.finance.modules.ledger.domain.exception.InUseException;
import com.uit.finance.shared.kernel.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountTest {

  @Test
  @DisplayName("đổi tên thì không cần hỏi ví có giao dịch chưa")
  void renameDoesNotQueryUsage() {
    Account account = account(ANN, "VND");

    boolean changed =
        account.revise(
            AccountDetails.parse("Ví mới", "CASH", "VND", 0, 0),
            () -> {
              throw new AssertionError("usage must not be queried");
            });

    assertThat(changed).isTrue();
    assertThat(account.getDetails().name().value()).isEqualTo("Ví mới");
  }

  @Test
  @DisplayName("dữ liệu y nguyên thì trả false để bỏ qua lần ghi")
  void unchangedReviseIsNoOp() {
    Account account = account(ANN, "VND");

    assertThat(account.revise(accountDetails("VND"), () -> true)).isFalse();
  }

  @Test
  @DisplayName("ví đã có giao dịch thì khoá tiền tệ (D8), chưa có thì đổi được")
  void currencyLockedOnceReferenced() {
    Account used = account(ANN, "VND");
    Account fresh = account(ANN, "VND");

    assertRejected(
        () -> used.revise(accountDetails("USD"), () -> true), CurrencyLockedException.CODE);
    assertThat(fresh.revise(accountDetails("USD"), () -> false)).isTrue();
    assertThat(fresh.currency().getCurrencyCode()).isEqualTo("USD");
  }

  @Test
  @DisplayName("archive rồi archive lần nữa là no-op; unarchive xoá mốc thời gian")
  void archiveIsIdempotent() {
    Account account = account(ANN, "VND");

    assertThat(account.changeArchived(true, NOW)).isTrue();
    assertThat(account.changeArchived(true, NOW.plusSeconds(60))).isFalse();
    assertThat(account.getArchivedAt()).isEqualTo(NOW);
    assertThat(account.changeArchived(false, NOW)).isTrue();
    assertThat(account.getArchivedAt()).isNull();
  }

  @Test
  @DisplayName("ví có giao dịch không xoá được (D7); xoá lần hai là no-op")
  void deleteRules() {
    Account used = account(ANN, "VND");
    Account fresh = account(ANN, "VND");

    assertRejected(() -> used.delete(NOW, () -> true), InUseException.CODE);
    assertThat(fresh.delete(NOW, () -> false)).isTrue();
    assertThat(
            fresh.delete(
                NOW,
                () -> {
                  throw new AssertionError("already deleted: usage must not be queried");
                }))
        .isFalse();
  }

  @Test
  @DisplayName("ví đã xoá thì không sửa, không archive được (xoá thắng, S4)")
  void deletedAccountIsFrozen() {
    Account account = account(ANN, "VND");
    account.delete(NOW, () -> false);

    assertRejected(
        () -> account.revise(accountDetails("VND"), () -> false), EntityDeletedException.CODE);
    assertRejected(() -> account.changeArchived(true, NOW), EntityDeletedException.CODE);
  }

  @Test
  @DisplayName("số dư = ban đầu + thu − chi − chuyển đi + chuyển đến")
  void balanceFormula() {
    Account account =
        Account.open(
            new AccountId(java.util.UUID.randomUUID()),
            ANN,
            AccountDetails.parse("Ví", "BANK", "VND", 1_000_000, 0));

    Money balance =
        account.balance(new AccountFlows(account.getId(), 500_000, 200_000, 300_000, 50_000));

    assertThat(balance).isEqualTo(Money.of(1_050_000, "VND"));
  }
}
