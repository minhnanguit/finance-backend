package com.uit.finance.modules.ledger.domain.model.transaction;

import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.ANN;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.account;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.assertInvalidField;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.assertRejected;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.category;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.expense;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.income;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.transfer;
import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.modules.ledger.domain.exception.InvalidTransferException;
import com.uit.finance.modules.ledger.domain.model.account.Account;
import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.modules.ledger.domain.model.category.CategoryKind;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerLimits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Hình dạng tự thân của giao dịch, chưa cần ví hay danh mục thật. */
class TransactionDetailsTest {

  private final Account wallet = account(ANN, "VND");
  private final Account bank = account(ANN, "VND");
  private final Category food = category(ANN, CategoryKind.EXPENSE);

  @Test
  @DisplayName("số tiền từ 1 tới 10^15 đơn vị nhỏ nhất")
  void amountBounds() {
    long max = LedgerLimits.MAX_AMOUNT_MINOR;

    assertThat(expense(wallet, food).amount(1).build()).isNotNull();
    assertThat(expense(wallet, food).amount(max).build()).isNotNull();
    assertInvalidField(() -> expense(wallet, food).amount(0).build(), "amountMinor");
    assertInvalidField(() -> expense(wallet, food).amount(-5).build(), "amountMinor");
    assertInvalidField(() -> expense(wallet, food).amount(max + 1).build(), "amountMinor");
  }

  @Test
  @DisplayName("thu/chi bắt buộc có danh mục và không có ví đến")
  void incomeAndExpenseShape() {
    assertInvalidField(() -> expense(wallet, food).category(null).build(), "categoryId");
    assertInvalidField(
        () -> income(wallet, food).counterAccount(bank.getId().value()).build(),
        "counterAccountId");
  }

  @Test
  @DisplayName("chuyển tiền: có ví đến, khác ví đi, không có danh mục")
  void transferShape() {
    assertThat(transfer(wallet, bank).build().type()).isEqualTo(TransactionType.TRANSFER);
    assertRejected(
        () -> transfer(wallet, bank).counterAccount(null).build(), InvalidTransferException.CODE);
    assertRejected(() -> transfer(wallet, wallet).build(), InvalidTransferException.CODE);
    assertInvalidField(
        () -> transfer(wallet, bank).category(food.getId().value()).build(), "categoryId");
  }

  @Test
  @DisplayName("ngày giao dịch bắt buộc; người nhận, ghi chú có giới hạn")
  void requiredDateAndBoundedText() {
    assertInvalidField(() -> expense(wallet, food).on(null).build(), "occurredOn");
    assertInvalidField(() -> expense(wallet, food).payee("x".repeat(101)).build(), "payee");
    assertInvalidField(() -> expense(wallet, food).note("x".repeat(501)).build(), "note");
  }

  @Test
  @DisplayName("toString không lộ số tiền, người nhận, ghi chú (B8)")
  void toStringIsRedacted() {
    String text =
        expense(wallet, food).amount(123_456).payee("Nhà thuốc").note("khám").build().toString();

    assertThat(text).doesNotContain("123456").doesNotContain("Nhà thuốc").doesNotContain("khám");
  }
}
