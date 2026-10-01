package com.uit.finance.modules.ledger.application.service;

import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.ANN;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.BOB;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.NOW;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.TODAY;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.category;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.expense;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.income;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.refs;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.transfer;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.transferRefs;
import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.modules.ledger.application.port.in.GetBalancesUseCase.AccountBalance;
import com.uit.finance.modules.ledger.application.port.in.GetBalancesUseCase.Balances;
import com.uit.finance.modules.ledger.application.port.in.GetBalancesUseCase.CurrencyTotal;
import com.uit.finance.modules.ledger.domain.model.Account;
import com.uit.finance.modules.ledger.domain.model.AccountDetails;
import com.uit.finance.modules.ledger.domain.model.AccountId;
import com.uit.finance.modules.ledger.domain.model.Category;
import com.uit.finance.modules.ledger.domain.model.CategoryKind;
import com.uit.finance.modules.ledger.domain.model.Transaction;
import com.uit.finance.modules.ledger.domain.model.TransactionDetails;
import com.uit.finance.modules.ledger.domain.model.TransactionId;
import com.uit.finance.modules.ledger.domain.model.TransactionReferences;
import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetBalancesServiceTest {

  private final LedgerFakes.Store store = new LedgerFakes.Store();
  private final GetBalancesService service = new GetBalancesService(store, store);

  private Account open(UserId owner, long opening) {
    Account account =
        Account.open(
            new AccountId(UUID.randomUUID()),
            owner,
            AccountDetails.parse("Ví", "BANK", "VND", opening, 0));
    store.put(account);
    return account;
  }

  private Transaction put(UserId owner, TransactionDetails details, TransactionReferences refs) {
    Transaction transaction =
        Transaction.record(new TransactionId(UUID.randomUUID()), owner, details, refs, TODAY, NOW);
    store.put(transaction);
    return transaction;
  }

  @Test
  @DisplayName("số dư tính từ lịch sử: bỏ DRAFT, bỏ giao dịch đã xoá, bỏ dữ liệu user khác")
  void balancesFromHistory() {
    Account wallet = open(ANN, 100_000);
    Account bank = open(ANN, 0);
    Category food = category(ANN, CategoryKind.EXPENSE);
    Category salary = category(ANN, CategoryKind.INCOME);
    put(ANN, expense(wallet, food).amount(30_000).build(), refs(wallet, food));
    put(ANN, expense(wallet, food).amount(999).status("DRAFT").build(), refs(wallet, food));
    put(ANN, income(wallet, salary).amount(50_000).build(), refs(wallet, salary));
    put(ANN, transfer(wallet, bank).amount(20_000).build(), transferRefs(wallet, bank));
    Transaction removed = put(ANN, expense(wallet, food).amount(7_000).build(), refs(wallet, food));
    removed.delete(NOW);
    store.put(removed);
    Account bobs = open(BOB, 0);
    Category bobsFood = category(BOB, CategoryKind.EXPENSE);
    put(BOB, expense(bobs, bobsFood).amount(1).build(), refs(bobs, bobsFood));

    Balances balances = service.balances(ANN);

    assertThat(balances.accounts())
        .containsExactly(
            new AccountBalance(wallet.getId().value(), 100_000, "VND", false),
            new AccountBalance(bank.getId().value(), 20_000, "VND", false));
    assertThat(balances.totals()).containsExactly(new CurrencyTotal("VND", 120_000));
  }
}
