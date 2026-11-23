package com.uit.finance.modules.recurring.application.service;

import com.uit.finance.modules.ledger.application.port.in.reference.AccountRef;
import com.uit.finance.modules.ledger.application.port.in.reference.CategoryRef;
import com.uit.finance.modules.ledger.application.port.in.reference.LedgerReferenceQuery;
import com.uit.finance.modules.recurring.application.port.out.LoadRecurringChangesPort;
import com.uit.finance.modules.recurring.application.port.out.LoadRecurringPort;
import com.uit.finance.modules.recurring.application.port.out.RecurringCountPort;
import com.uit.finance.modules.recurring.application.port.out.RecurringWriteLockPort;
import com.uit.finance.modules.recurring.application.port.out.SaveRecurringPort;
import com.uit.finance.modules.recurring.application.port.out.Sequenced;
import com.uit.finance.modules.recurring.domain.model.Recurring;
import com.uit.finance.modules.recurring.domain.model.RecurringId;
import com.uit.finance.shared.kernel.UserId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Fake in-memory cho mọi port out của recurring và cho {@code ledger::reference}. */
final class RecurringFakes {

  private RecurringFakes() {}

  static final class Store
      implements LoadRecurringPort,
          SaveRecurringPort,
          RecurringCountPort,
          LoadRecurringChangesPort,
          RecurringWriteLockPort {

    final Map<UUID, Recurring> rows = new HashMap<>();
    final Map<UUID, Long> seqs = new HashMap<>();
    final List<UserId> locks = new ArrayList<>();
    long seq;

    @Override
    public Optional<Recurring> find(UserId owner, RecurringId id) {
      return Optional.ofNullable(rows.get(id.value())).filter(r -> r.isOwnedBy(owner));
    }

    @Override
    public boolean insertIfAbsent(UserId owner, Recurring recurring) {
      if (rows.containsKey(recurring.getId().value())) {
        return false;
      }
      rows.put(recurring.getId().value(), recurring);
      seqs.put(recurring.getId().value(), ++seq);
      return true;
    }

    @Override
    public void update(UserId owner, Recurring recurring) {
      rows.put(recurring.getId().value(), recurring);
      seqs.put(recurring.getId().value(), ++seq);
    }

    @Override
    public long countStored(UserId owner) {
      return rows.values().stream().filter(r -> r.isOwnedBy(owner) && !r.isDeleted()).count();
    }

    @Override
    public long countActive(UserId owner, LocalDate endsNotBefore) {
      return rows.values().stream()
          .filter(r -> r.isOwnedBy(owner) && !r.isDeleted())
          .filter(
              r ->
                  r.getTerms().generationEndDate() == null
                      || !r.getTerms().generationEndDate().isBefore(endsNotBefore))
          .count();
    }

    @Override
    public List<Sequenced<Recurring>> changesSince(UserId owner, long afterSeq, int limit) {
      return rows.values().stream()
          .filter(r -> r.isOwnedBy(owner))
          .map(r -> new Sequenced<>(r, seqs.get(r.getId().value())))
          .filter(s -> s.changeSeq() > afterSeq)
          .sorted(Comparator.comparingLong(Sequenced::changeSeq))
          .limit(limit)
          .toList();
    }

    @Override
    public Optional<Sequenced<Recurring>> findSequenced(UserId owner, RecurringId id) {
      return find(owner, id).map(r -> new Sequenced<>(r, seqs.get(id.value())));
    }

    @Override
    public void lock(UserId owner) {
      locks.add(owner);
    }
  }

  static final class Ledger implements LedgerReferenceQuery {

    final Map<UserId, Map<UUID, AccountRef>> accounts = new HashMap<>();
    final Map<UserId, Map<UUID, CategoryRef>> categories = new HashMap<>();
    int accountLookups;

    UUID account(UserId owner, String currency, boolean deleted) {
      UUID id = UUID.randomUUID();
      accounts
          .computeIfAbsent(owner, k -> new HashMap<>())
          .put(id, new AccountRef(id, currency, false, deleted));
      return id;
    }

    UUID category(UserId owner, String kind, boolean deleted) {
      UUID id = UUID.randomUUID();
      categories
          .computeIfAbsent(owner, k -> new HashMap<>())
          .put(id, new CategoryRef(id, kind, null, false, deleted));
      return id;
    }

    @Override
    public Optional<CategoryRef> findCategory(UserId owner, UUID categoryId) {
      return Optional.ofNullable(categories.getOrDefault(owner, Map.of()).get(categoryId));
    }

    @Override
    public List<CategoryRef> findCategories(UserId owner, Set<UUID> categoryIds) {
      Map<UUID, CategoryRef> mine = categories.getOrDefault(owner, Map.of());
      return categoryIds.stream().map(mine::get).filter(ref -> ref != null).toList();
    }

    @Override
    public Optional<AccountRef> findAccount(UserId owner, UUID accountId) {
      accountLookups++;
      return Optional.ofNullable(accounts.getOrDefault(owner, Map.of()).get(accountId));
    }
  }
}
