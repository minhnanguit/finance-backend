package com.uit.finance.modules.ledger.domain.model;

import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.assertInvalidField;
import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.modules.ledger.domain.model.account.AccountDetails;
import com.uit.finance.modules.ledger.domain.model.account.AccountId;
import com.uit.finance.modules.ledger.domain.model.account.AccountType;
import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import com.uit.finance.modules.ledger.domain.model.category.CategoryKind;
import com.uit.finance.modules.ledger.domain.model.category.ColorHex;
import com.uit.finance.modules.ledger.domain.model.category.IconName;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerLimits;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerName;
import com.uit.finance.modules.ledger.domain.model.transaction.Note;
import com.uit.finance.modules.ledger.domain.model.transaction.Payee;
import com.uit.finance.modules.ledger.domain.model.transaction.TransactionId;
import com.uit.finance.modules.ledger.domain.model.transaction.TransactionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValueObjectsTest {

  @Test
  @DisplayName("tên được cắt khoảng trắng; rỗng hoặc quá 50 ký tự là invalid_field")
  void nameIsTrimmedAndBounded() {
    assertThat(new LedgerName("  Ví  ").value()).isEqualTo("Ví");
    assertInvalidField(() -> new LedgerName("   "), "name");
    assertInvalidField(() -> new LedgerName(null), "name");
    assertInvalidField(() -> new LedgerName("x".repeat(51)), "name");
  }

  @Test
  @DisplayName("độ dài đếm theo ký tự thật: 50 emoji vẫn hợp lệ dù Java thấy 100 char")
  void lengthCountsCodePoints() {
    String fiftyEmoji = "😀".repeat(50);

    assertThat(fiftyEmoji.length()).isEqualTo(100);
    assertThat(new LedgerName(fiftyEmoji).value()).isEqualTo(fiftyEmoji);
  }

  @Test
  @DisplayName("người nhận và ghi chú: rỗng là không có, quá giới hạn là invalid_field")
  void payeeAndNoteAreOptionalButBounded() {
    assertThat(Payee.parse("   ")).isNull();
    assertThat(Payee.parse(null)).isNull();
    assertThat(Payee.parse(" Phở Thìn ").value()).isEqualTo("Phở Thìn");
    assertInvalidField(() -> Payee.parse("x".repeat(101)), "payee");

    assertThat(Note.parse("x".repeat(500))).isNotNull();
    assertInvalidField(() -> Note.parse("x".repeat(501)), "note");
  }

  @Test
  @DisplayName("toString không lộ tên, người nhận, ghi chú (B8)")
  void sensitiveTextIsRedacted() {
    assertThat(new LedgerName("Lương bí mật").toString()).doesNotContain("Lương");
    assertThat(new Payee("Nhà thuốc A").toString()).doesNotContain("Nhà thuốc");
    assertThat(new Note("khám bệnh").toString()).doesNotContain("khám");
  }

  @Test
  @DisplayName("màu chuẩn hoá về chữ hoa; icon chỉ nhận [a-z0-9_]")
  void iconAndColorShapes() {
    assertThat(ColorHex.parse("#f97316").value()).isEqualTo("#F97316");
    assertThat(ColorHex.parse(" ")).isNull();
    assertInvalidField(() -> ColorHex.parse("red"), "color");

    assertThat(IconName.parse("shopping_bag").value()).isEqualTo("shopping_bag");
    assertInvalidField(() -> IconName.parse("Shopping Bag"), "icon");
  }

  @Test
  @DisplayName("enum và tiền tệ sai là invalid_field, không phải lỗi 500")
  void enumsAndCurrencies() {
    assertInvalidField(() -> AccountType.parse("SAVINGS"), "type");
    assertInvalidField(() -> AccountType.parse(null), "type");
    assertInvalidField(() -> CategoryKind.parse("income"), "kind");
    assertInvalidField(() -> TransactionStatus.parse("PENDING"), "status");

    assertInvalidField(() -> AccountDetails.parse("Ví", "CASH", "vnd", 0, 0), "currency");
    assertInvalidField(() -> AccountDetails.parse("Ví", "CASH", "ZZZ", 0, 0), "currency");
    assertInvalidField(() -> AccountDetails.parse("Ví", "CASH", null, 0, 0), "currency");
  }

  @Test
  @DisplayName("số dư ban đầu có dấu, |x| ≤ 10^15")
  void openingBalanceIsSignedAndBounded() {
    long max = LedgerLimits.MAX_AMOUNT_MINOR;

    assertThat(AccountDetails.parse("Ví", "BANK", "VND", -max, 0).openingBalance().amountMinor())
        .isEqualTo(-max);
    assertThat(AccountDetails.parse("Ví", "BANK", "VND", max, 0)).isNotNull();
    assertInvalidField(
        () -> AccountDetails.parse("Ví", "BANK", "VND", max + 1, 0), "openingBalanceMinor");
    assertInvalidField(
        () -> AccountDetails.parse("Ví", "BANK", "VND", -max - 1, 0), "openingBalanceMinor");
  }

  @Test
  @DisplayName("id thiếu từ client là invalid_field")
  void missingIdsAreInvalidFields() {
    assertInvalidField(() -> AccountId.of(null), "accountId");
    assertInvalidField(() -> CategoryId.of(null), "categoryId");
    assertInvalidField(() -> TransactionId.of(null), "transactionId");
    assertThat(AccountId.ofNullable(null)).isNull();
  }
}
