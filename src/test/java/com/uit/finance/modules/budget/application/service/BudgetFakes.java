package com.uit.finance.modules.budget.application.service;

import com.uit.finance.modules.budget.application.port.out.BudgetCountPort;
import com.uit.finance.modules.budget.application.port.out.BudgetWriteLockPort;
import com.uit.finance.modules.budget.application.port.out.LoadBudgetChangesPort;
import com.uit.finance.modules.budget.application.port.out.LoadBudgetPort;
import com.uit.finance.modules.budget.application.port.out.SaveBudgetPort;
import com.uit.finance.modules.budget.application.port.out.Sequenced;
import com.uit.finance.modules.budget.domain.exception.DuplicateBudgetException;
import com.uit.finance.modules.budget.domain.model.Budget;
import com.uit.finance.modules.budget.domain.model.BudgetId;
import com.uit.finance.modules.ledger.application.port.in.reference.CategoryRef;
import com.uit.finance.modules.ledger.application.port.in.reference.LedgerReferenceQuery;
import com.uit.finance.shared.kernel.UserId;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** Fake in-memory cho port của budget và cho {@code ledger::reference} (testing-policy). */
final class BudgetFakes {

  private BudgetFakes() {}

  /**
   * Một "bảng" {@code budgets}. Lưu và trả **bản copy** như DB thật; mọi query lọc theo {@code
   * owner} (B1). Ghi khi chưa {@link #lock} thì ném lỗi, để test bắt được use case quên khoá (GS7).
   */
  static final class Store
      implements LoadBudgetPort,
          SaveBudgetPort,
          BudgetCountPort,
          BudgetWriteLockPort,
          LoadBudgetChangesPort {

    final Map<UUID, Budget> rows = new LinkedHashMap<>();
    final Map<UUID, Long> changeSeqs = new HashMap<>();
    final Set<UserId> locked = new HashSet<>();
    int inserts;
    int updates;
    private long lastSeq;

    void put(Budget budget) {
      rows.put(budget.getId().value(), copy(budget));
      changeSeqs.put(budget.getId().value(), ++lastSeq);
    }

    /** Giả lập transaction mới: khoá của transaction trước đã nhả. */
    void releaseLocks() {
      locked.clear();
    }

    @Override
    public void lock(UserId owner) {
      locked.add(owner);
    }

    @Override
    public Optional<Budget> find(UserId owner, BudgetId id) {
      return Optional.ofNullable(rows.get(id.value()))
          .filter(budget -> budget.isOwnedBy(owner))
          .map(Store::copy);
    }

    @Override
    public Optional<Budget> findActive(UserId owner, UUID categoryId, Currency currency) {
      return active(owner)
          .filter(
              budget ->
                  budget.getTerms().categoryId().equals(categoryId)
                      && budget.getTerms().currency().equals(currency))
          .findFirst();
    }

    @Override
    public List<Budget> findActiveByCategory(UserId owner, UUID categoryId) {
      return active(owner)
          .filter(budget -> budget.getTerms().categoryId().equals(categoryId))
          .toList();
    }

    @Override
    public List<Budget> findAllActive(UserId owner) {
      return active(owner).toList();
    }

    @Override
    public long countActive(UserId owner) {
      return active(owner).count();
    }

    /** Như adapter thật: id đã có thì {@code false}, đụng unique index thì duplicate. */
    @Override
    public boolean insertIfAbsent(UserId owner, Budget budget) {
      requireLocked(owner);
      if (rows.containsKey(budget.getId().value())) {
        return false;
      }
      if (findActive(owner, budget.getTerms().categoryId(), budget.getTerms().currency())
          .isPresent()) {
        throw new DuplicateBudgetException(budget.getTerms().categoryId());
      }
      inserts++;
      put(budget);
      return true;
    }

    @Override
    public void update(UserId owner, Budget budget) {
      requireLocked(owner);
      Budget current = rows.get(budget.getId().value());
      if (current != null && current.isOwnedBy(owner)) {
        updates++;
        put(budget);
      }
    }

    @Override
    public List<Sequenced<Budget>> changesSince(UserId owner, long afterSeq, int limit) {
      return rows.values().stream()
          .filter(budget -> budget.isOwnedBy(owner))
          .map(budget -> new Sequenced<>(copy(budget), changeSeqs.get(budget.getId().value())))
          .filter(sequenced -> sequenced.changeSeq() > afterSeq)
          .sorted(Comparator.comparingLong(Sequenced::changeSeq))
          .limit(limit)
          .toList();
    }

    @Override
    public Optional<Sequenced<Budget>> findSequenced(UserId owner, BudgetId id) {
      return find(owner, id)
          .map(budget -> new Sequenced<>(budget, changeSeqs.get(budget.getId().value())));
    }

    private Stream<Budget> active(UserId owner) {
      return rows.values().stream()
          .filter(budget -> budget.isOwnedBy(owner) && !budget.isDeleted())
          .map(Store::copy);
    }

    private void requireLocked(UserId owner) {
      if (!locked.contains(owner)) {
        throw new AssertionError("write without BudgetWriteLockPort.lock (ADR-007 GS7)");
      }
    }

    private static Budget copy(Budget budget) {
      return Budget.rehydrate(
          budget.getId(), budget.getOwner(), budget.getTerms(), budget.getDeletedAt());
    }
  }

  /** Danh mục của ledger theo từng user; đếm số lần gọi để kiểm tra lô (GS8). */
  static final class Ledger implements LedgerReferenceQuery {

    private final Map<UserId, Map<UUID, CategoryRef>> categories = new HashMap<>();
    int singleLookups;
    int batchLookups;

    UUID expense(UserId owner) {
      return put(owner, "EXPENSE", null, false, false);
    }

    UUID put(
        UserId owner, String kind, @Nullable UUID parentId, boolean archived, boolean deleted) {
      UUID id = UUID.randomUUID();
      set(owner, new CategoryRef(id, kind, parentId, archived, deleted));
      return id;
    }

    void set(UserId owner, CategoryRef ref) {
      categories.computeIfAbsent(owner, key -> new HashMap<>()).put(ref.id(), ref);
    }

    CategoryRef get(UserId owner, UUID id) {
      return categories.get(owner).get(id);
    }

    @Override
    public Optional<CategoryRef> findCategory(UserId owner, UUID categoryId) {
      singleLookups++;
      return Optional.ofNullable(categories.getOrDefault(owner, Map.of()).get(categoryId));
    }

    @Override
    public List<CategoryRef> findCategories(UserId owner, Set<UUID> categoryIds) {
      batchLookups++;
      Map<UUID, CategoryRef> mine = categories.getOrDefault(owner, Map.of());
      return categoryIds.stream().map(mine::get).filter(ref -> ref != null).toList();
    }
  }
}
