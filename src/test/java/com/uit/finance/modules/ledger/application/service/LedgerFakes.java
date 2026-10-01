package com.uit.finance.modules.ledger.application.service;

import com.uit.finance.modules.ledger.application.port.out.AccountUsagePort;
import com.uit.finance.modules.ledger.application.port.out.CategoryUsagePort;
import com.uit.finance.modules.ledger.application.port.out.LoadAccountFlowsPort;
import com.uit.finance.modules.ledger.application.port.out.LoadAccountPort;
import com.uit.finance.modules.ledger.application.port.out.LoadCategoryPort;
import com.uit.finance.modules.ledger.application.port.out.LoadTransactionPort;
import com.uit.finance.modules.ledger.application.port.out.SaveAccountPort;
import com.uit.finance.modules.ledger.application.port.out.SaveCategoryPort;
import com.uit.finance.modules.ledger.application.port.out.SaveTransactionPort;
import com.uit.finance.modules.ledger.application.port.out.SeedCategoriesPort;
import com.uit.finance.modules.ledger.domain.model.Account;
import com.uit.finance.modules.ledger.domain.model.AccountFlows;
import com.uit.finance.modules.ledger.domain.model.AccountId;
import com.uit.finance.modules.ledger.domain.model.Category;
import com.uit.finance.modules.ledger.domain.model.CategoryId;
import com.uit.finance.modules.ledger.domain.model.Transaction;
import com.uit.finance.modules.ledger.domain.model.TransactionDetails;
import com.uit.finance.modules.ledger.domain.model.TransactionId;
import com.uit.finance.modules.ledger.domain.model.TransactionStatus;
import com.uit.finance.shared.kernel.DomainEvent;
import com.uit.finance.shared.kernel.DomainEventPublisher;
import com.uit.finance.shared.kernel.UserId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Fake in-memory cho port của ledger. Dùng fake thay mock để đọc được behavior (testing-policy).
 */
final class LedgerFakes {

  private LedgerFakes() {}

  /**
   * Một "database" cho cả 3 bảng. Luôn lưu và trả **bản copy** như DB thật, nên service quên gọi
   * {@code update} thì test thấy ngay. Mọi query lọc theo {@code owner} như adapter thật (B1).
   */
  static final class Store
      implements LoadAccountPort,
          SaveAccountPort,
          AccountUsagePort,
          LoadCategoryPort,
          SaveCategoryPort,
          SeedCategoriesPort,
          CategoryUsagePort,
          LoadTransactionPort,
          SaveTransactionPort,
          LoadAccountFlowsPort {

    final Map<UUID, Account> accounts = new LinkedHashMap<>();
    final Map<UUID, Category> categories = new LinkedHashMap<>();
    final Map<UUID, Transaction> transactions = new LinkedHashMap<>();
    int inserts;
    int updates;

    /** Chạy ngay trước mỗi insert, để giả lập một request song song vừa chen vào. */
    Runnable beforeInsert = () -> {};

    void put(Account account) {
      accounts.put(account.getId().value(), copy(account));
    }

    void put(Category category) {
      categories.put(category.getId().value(), copy(category));
    }

    void put(Transaction transaction) {
      transactions.put(transaction.getId().value(), copy(transaction));
    }

    Account account(Account account) {
      return accounts.get(account.getId().value());
    }

    // ---- accounts ----

    @Override
    public Optional<Account> find(UserId owner, AccountId id) {
      return Optional.ofNullable(accounts.get(id.value()))
          .filter(account -> account.isOwnedBy(owner))
          .map(Store::copy);
    }

    @Override
    public List<Account> listAccounts(UserId owner) {
      return accounts.values().stream()
          .filter(account -> account.isOwnedBy(owner) && !account.isDeleted())
          .map(Store::copy)
          .toList();
    }

    @Override
    public boolean insertIfAbsent(UserId owner, Account account) {
      requireOwner(owner, account.getOwner());
      beforeInsert.run();
      if (accounts.containsKey(account.getId().value())) {
        return false;
      }
      inserts++;
      put(account);
      return true;
    }

    @Override
    public void update(UserId owner, Account account) {
      requireOwner(owner, account.getOwner());
      Account current = accounts.get(account.getId().value());
      if (current != null && current.isOwnedBy(owner)) {
        updates++;
        put(account);
      }
    }

    @Override
    public long countAccounts(UserId owner) {
      return accounts.values().stream()
          .filter(account -> account.isOwnedBy(owner) && !account.isDeleted())
          .count();
    }

    @Override
    public boolean isReferenced(UserId owner, AccountId id) {
      return liveTransactions(owner)
          .anyMatch(
              details -> details.accountId().equals(id) || id.equals(details.counterAccountId()));
    }

    // ---- categories ----

    @Override
    public Optional<Category> find(UserId owner, CategoryId id) {
      return Optional.ofNullable(categories.get(id.value()))
          .filter(category -> category.isOwnedBy(owner))
          .map(Store::copy);
    }

    @Override
    public boolean insertIfAbsent(UserId owner, Category category) {
      requireOwner(owner, category.getOwner());
      beforeInsert.run();
      if (categories.containsKey(category.getId().value())) {
        return false;
      }
      inserts++;
      put(category);
      return true;
    }

    @Override
    public void update(UserId owner, Category category) {
      requireOwner(owner, category.getOwner());
      Category current = categories.get(category.getId().value());
      if (current != null && current.isOwnedBy(owner)) {
        updates++;
        put(category);
      }
    }

    @Override
    public int insertAllIfAbsent(UserId owner, List<Category> batch) {
      int inserted = 0;
      for (Category category : batch) {
        requireOwner(owner, category.getOwner());
        if (!categories.containsKey(category.getId().value())) {
          put(category);
          inserted++;
        }
      }
      return inserted;
    }

    @Override
    public long countCategories(UserId owner) {
      return categories.values().stream()
          .filter(category -> category.isOwnedBy(owner) && !category.isDeleted())
          .count();
    }

    @Override
    public boolean isReferenced(UserId owner, CategoryId id) {
      return liveTransactions(owner).anyMatch(details -> id.equals(details.categoryId()));
    }

    @Override
    public boolean hasActiveChildren(UserId owner, CategoryId id) {
      return categories.values().stream()
          .anyMatch(
              category ->
                  category.isOwnedBy(owner)
                      && !category.isDeleted()
                      && id.equals(category.getDetails().parentId()));
    }

    // ---- transactions ----

    @Override
    public Optional<Transaction> find(UserId owner, TransactionId id) {
      return Optional.ofNullable(transactions.get(id.value()))
          .filter(transaction -> transaction.getOwner().equals(owner))
          .map(Store::copy);
    }

    @Override
    public boolean insertIfAbsent(UserId owner, Transaction transaction) {
      requireOwner(owner, transaction.getOwner());
      beforeInsert.run();
      if (transactions.containsKey(transaction.getId().value())) {
        return false;
      }
      inserts++;
      put(transaction);
      return true;
    }

    @Override
    public void update(UserId owner, Transaction transaction) {
      requireOwner(owner, transaction.getOwner());
      Transaction current = transactions.get(transaction.getId().value());
      if (current != null && current.getOwner().equals(owner)) {
        updates++;
        put(transaction);
      }
    }

    /** Bản SQL ở Phase 2 phải cho ra cùng kết quả: chỉ CONFIRMED, chưa xoá, của đúng user. */
    @Override
    public List<AccountFlows> flowsByAccount(UserId owner) {
      Map<AccountId, long[]> sums = new LinkedHashMap<>();
      liveTransactions(owner)
          .filter(details -> details.status() == TransactionStatus.CONFIRMED)
          .forEach(
              details -> {
                long amount = details.amount().amountMinor();
                long[] from = sums.computeIfAbsent(details.accountId(), id -> new long[4]);
                switch (details.type()) {
                  case INCOME -> from[0] += amount;
                  case EXPENSE -> from[1] += amount;
                  case TRANSFER -> {
                    from[2] += amount;
                    sums.computeIfAbsent(
                                Objects.requireNonNull(details.counterAccountId()),
                                id -> new long[4])[3] +=
                        amount;
                  }
                }
              });
      return sums.entrySet().stream()
          .map(
              e ->
                  new AccountFlows(
                      e.getKey(),
                      e.getValue()[0],
                      e.getValue()[1],
                      e.getValue()[2],
                      e.getValue()[3]))
          .toList();
    }

    private java.util.stream.Stream<TransactionDetails> liveTransactions(UserId owner) {
      return transactions.values().stream()
          .filter(transaction -> transaction.getOwner().equals(owner) && !transaction.isDeleted())
          .map(Transaction::getDetails);
    }

    /** Hợp đồng port: {@code owner} truyền vào phải là chủ của bản ghi. */
    private static void requireOwner(UserId owner, UserId recordOwner) {
      if (!owner.equals(recordOwner)) {
        throw new AssertionError(
            "port called with owner " + owner + " for a record of " + recordOwner);
      }
    }

    private static Account copy(Account a) {
      return Account.rehydrate(
          a.getId(), a.getOwner(), a.getDetails(), a.getArchivedAt(), a.getDeletedAt());
    }

    private static Category copy(Category c) {
      return Category.rehydrate(
          c.getId(),
          c.getOwner(),
          c.getKind(),
          c.getTemplateKey(),
          c.getDetails(),
          c.getArchivedAt(),
          c.getDeletedAt());
    }

    private static Transaction copy(Transaction t) {
      return Transaction.rehydrate(t.getId(), t.getOwner(), t.getDetails(), t.getDeletedAt());
    }
  }

  static final class RecordingEvents implements DomainEventPublisher {

    final List<DomainEvent> published = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
      published.add(event);
    }
  }
}
