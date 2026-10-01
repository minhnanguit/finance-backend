package com.uit.finance.modules.ledger.domain.model.transaction;

import static com.uit.finance.modules.ledger.domain.model.shared.LedgerLimits.MAX_AMOUNT_MINOR;

import com.uit.finance.modules.ledger.domain.exception.InvalidFieldException;
import com.uit.finance.modules.ledger.domain.exception.InvalidTransferException;
import com.uit.finance.modules.ledger.domain.model.account.AccountId;
import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import com.uit.finance.modules.ledger.domain.model.shared.Fields;
import com.uit.finance.shared.kernel.Ensure;
import com.uit.finance.shared.kernel.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Phần client được sửa của một giao dịch. Constructor kiểm hình dạng tự thân (số tiền, field nào đi
 * với type nào); luật cần ví và danh mục thật nằm ở {@link TransactionRules}.
 *
 * @param occurredOn ngày theo lịch của user (ADR-005 D5)
 * @param occurredAt thời điểm, {@code null} khi không rõ giờ
 */
public record TransactionDetails(
    TransactionType type,
    TransactionStatus status,
    AccountId accountId,
    @Nullable AccountId counterAccountId,
    Money amount,
    @Nullable CategoryId categoryId,
    LocalDate occurredOn,
    @Nullable Instant occurredAt,
    @Nullable Payee payee,
    @Nullable Note note) {

  public TransactionDetails {
    Ensure.notNull(type, "type");
    Ensure.notNull(status, "status");
    Ensure.notNull(accountId, "accountId");
    Ensure.notNull(amount, "amount");
    Fields.required(occurredOn, "occurredOn");
    Fields.between(amount.amountMinor(), 1, MAX_AMOUNT_MINOR, "amountMinor");
    if (type.isTransfer()) {
      if (counterAccountId == null) {
        throw new InvalidTransferException("a transfer needs a destination account");
      }
      if (counterAccountId.equals(accountId)) {
        throw new InvalidTransferException("source and destination must be different accounts");
      }
      if (categoryId != null) {
        throw new InvalidFieldException("categoryId");
      }
    } else {
      if (counterAccountId != null) {
        throw new InvalidFieldException("counterAccountId");
      }
      if (categoryId == null) {
        throw new InvalidFieldException("categoryId");
      }
    }
  }

  public static TransactionDetails parse(
      @Nullable String type,
      @Nullable String status,
      @Nullable UUID accountId,
      @Nullable UUID counterAccountId,
      long amountMinor,
      @Nullable String currency,
      @Nullable UUID categoryId,
      @Nullable LocalDate occurredOn,
      @Nullable Instant occurredAt,
      @Nullable String payee,
      @Nullable String note) {
    return new TransactionDetails(
        TransactionType.parse(type),
        TransactionStatus.parse(status),
        AccountId.of(accountId),
        AccountId.ofNullable(counterAccountId),
        Money.of(amountMinor, Fields.currency(currency, "currency")),
        CategoryId.ofNullable(categoryId),
        Fields.required(occurredOn, "occurredOn"),
        occurredAt,
        Payee.parse(payee),
        Note.parse(note));
  }

  /** Không in số tiền, người nhận, ghi chú (ADR-006 B8). */
  @Override
  public String toString() {
    return "TransactionDetails[type=%s, status=%s, accountId=%s, occurredOn=%s]"
        .formatted(type, status, accountId, occurredOn);
  }
}
