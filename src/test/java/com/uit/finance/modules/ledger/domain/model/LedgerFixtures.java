package com.uit.finance.modules.ledger.domain.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.finance.modules.ledger.application.port.in.TransactionFields;
import com.uit.finance.modules.ledger.domain.exception.InvalidFieldException;
import com.uit.finance.shared.kernel.DomainException;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Function;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jspecify.annotations.Nullable;

/** Dữ liệu mẫu cho test ledger, dùng chung cho test domain và test use case. */
public final class LedgerFixtures {

  public static final Instant NOW = Instant.parse("2026-09-30T08:00:00Z");
  public static final LocalDate TODAY = LocalDate.of(2026, 9, 30);

  public static final UserId ANN = UserId.of("00000000-0000-0000-0000-00000000a001");
  public static final UserId BOB = UserId.of("00000000-0000-0000-0000-00000000b001");

  /** Dùng khi test chắc chắn không cần load danh mục cha. */
  public static final Function<CategoryId, Category> NO_PARENT =
      id -> {
        throw new AssertionError("parent " + id + " must not be loaded");
      };

  private LedgerFixtures() {}

  public static AccountDetails accountDetails(String currency) {
    return AccountDetails.parse("Ví tiền mặt", "CASH", currency, 0, 0);
  }

  public static Account account(UserId owner, String currency) {
    return Account.open(new AccountId(UUID.randomUUID()), owner, accountDetails(currency));
  }

  public static Account archived(Account account) {
    account.changeArchived(true, NOW);
    return account;
  }

  public static Account deleted(Account account) {
    account.delete(NOW, () -> false);
    return account;
  }

  public static Category category(UserId owner, CategoryKind kind) {
    return Category.create(
        new CategoryId(UUID.randomUUID()),
        owner,
        kind,
        CategoryDetails.parse("Ăn uống", null, null, null),
        NO_PARENT);
  }

  public static TxBuilder expense(Account account, Category category) {
    return new TxBuilder("EXPENSE", account.getId().value(), null, category.getId().value())
        .currency(account.currency().getCurrencyCode());
  }

  public static TxBuilder income(Account account, Category category) {
    return new TxBuilder("INCOME", account.getId().value(), null, category.getId().value())
        .currency(account.currency().getCurrencyCode());
  }

  public static TxBuilder transfer(Account from, Account to) {
    return new TxBuilder("TRANSFER", from.getId().value(), to.getId().value(), null)
        .currency(from.currency().getCurrencyCode());
  }

  public static TransactionReferences refs(Account account, @Nullable Category category) {
    return new TransactionReferences(account, null, category);
  }

  public static TransactionReferences transferRefs(Account from, Account to) {
    return new TransactionReferences(from, to, null);
  }

  /** Lỗi nghiệp vụ đúng mã. */
  public static void assertRejected(ThrowingCallable call, String code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> org.assertj.core.api.Assertions.assertThat(e.code()).isEqualTo(code));
  }

  /** {@code ledger.invalid_field} đúng tên field. */
  public static void assertInvalidField(ThrowingCallable call, String field) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            InvalidFieldException.class,
            e -> org.assertj.core.api.Assertions.assertThat(e.field()).isEqualTo(field));
  }

  /** Builder cho dữ liệu giao dịch, mặc định hợp lệ: 50.000 đ, CONFIRMED, hôm nay. */
  public static final class TxBuilder {

    private String type;
    private String status = "CONFIRMED";
    private @Nullable UUID accountId;
    private @Nullable UUID counterAccountId;
    private long amountMinor = 50_000;
    private @Nullable String currency;
    private @Nullable UUID categoryId;
    private LocalDate occurredOn = TODAY;
    private @Nullable String payee;
    private @Nullable String note;

    private TxBuilder(
        String type,
        @Nullable UUID accountId,
        @Nullable UUID counterAccountId,
        @Nullable UUID categoryId) {
      this.type = type;
      this.accountId = accountId;
      this.counterAccountId = counterAccountId;
      this.categoryId = categoryId;
    }

    public TxBuilder type(String type) {
      this.type = type;
      return this;
    }

    public TxBuilder status(String status) {
      this.status = status;
      return this;
    }

    public TxBuilder account(Account account) {
      this.accountId = account.getId().value();
      return this;
    }

    public TxBuilder counterAccount(@Nullable UUID counterAccountId) {
      this.counterAccountId = counterAccountId;
      return this;
    }

    public TxBuilder category(@Nullable UUID categoryId) {
      this.categoryId = categoryId;
      return this;
    }

    public TxBuilder amount(long amountMinor) {
      this.amountMinor = amountMinor;
      return this;
    }

    public TxBuilder currency(String currency) {
      this.currency = currency;
      return this;
    }

    public TxBuilder on(LocalDate occurredOn) {
      this.occurredOn = occurredOn;
      return this;
    }

    public TxBuilder payee(String payee) {
      this.payee = payee;
      return this;
    }

    public TxBuilder note(String note) {
      this.note = note;
      return this;
    }

    public TransactionDetails build() {
      return TransactionDetails.parse(
          type,
          status,
          accountId,
          counterAccountId,
          amountMinor,
          currency,
          categoryId,
          occurredOn,
          null,
          payee,
          note);
    }

    public TransactionFields fields() {
      return new TransactionFields(
          type,
          status,
          accountId,
          counterAccountId,
          amountMinor,
          currency,
          categoryId,
          occurredOn,
          null,
          payee,
          note);
    }
  }
}
