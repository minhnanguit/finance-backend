package com.uit.finance.modules.budget.adapter.out.persistence;

import com.uit.finance.modules.budget.application.port.out.BudgetCountPort;
import com.uit.finance.modules.budget.application.port.out.LoadBudgetChangesPort;
import com.uit.finance.modules.budget.application.port.out.LoadBudgetPort;
import com.uit.finance.modules.budget.application.port.out.SaveBudgetPort;
import com.uit.finance.modules.budget.application.port.out.Sequenced;
import com.uit.finance.modules.budget.domain.exception.BudgetNotFoundException;
import com.uit.finance.modules.budget.domain.exception.DuplicateBudgetException;
import com.uit.finance.modules.budget.domain.model.Budget;
import com.uit.finance.modules.budget.domain.model.BudgetId;
import com.uit.finance.shared.kernel.UserId;
import com.uit.finance.shared.sync.ChangeSequencer;
import java.time.Clock;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Đọc/ghi ngân sách. Mọi query lọc {@code user_id = :owner} (ADR-006 B1); mỗi lần ghi lấy một số
 * {@code change_seq} mới trong cùng transaction (ADR-002 §2).
 */
@Component
class BudgetPersistenceAdapter
    implements LoadBudgetPort, SaveBudgetPort, BudgetCountPort, LoadBudgetChangesPort {

  private static final String INSERT =
      """
      INSERT INTO budgets (id, user_id, category_id, currency, amount_minor, start_month,
                           change_seq, version, created_at, updated_at, deleted_at)
      VALUES (:id, :userId, :categoryId, :currency, :amountMinor, :startMonth,
              :changeSeq, 0, :now, :now, :deletedAt)
      ON CONFLICT (id) DO NOTHING
      """;

  private final BudgetJpaRepository repository;
  private final NamedParameterJdbcTemplate jdbc;
  private final ChangeSequencer sequencer;
  private final Clock clock;

  BudgetPersistenceAdapter(
      BudgetJpaRepository repository,
      NamedParameterJdbcTemplate jdbc,
      ChangeSequencer sequencer,
      Clock clock) {
    this.repository = repository;
    this.jdbc = jdbc;
    this.sequencer = sequencer;
    this.clock = clock;
  }

  @Override
  public Optional<Budget> find(UserId owner, BudgetId id) {
    return repository
        .findByIdAndUserId(id.value(), owner.value())
        .map(BudgetPersistenceMapper::toDomain);
  }

  @Override
  public Optional<Budget> findActive(UserId owner, UUID categoryId, Currency currency) {
    return repository
        .findByUserIdAndCategoryIdAndCurrencyAndDeletedAtIsNull(
            owner.value(), categoryId, currency.getCurrencyCode())
        .map(BudgetPersistenceMapper::toDomain);
  }

  @Override
  public List<Budget> findActiveByCategory(UserId owner, UUID categoryId) {
    return repository
        .findByUserIdAndCategoryIdAndDeletedAtIsNull(owner.value(), categoryId)
        .stream()
        .map(BudgetPersistenceMapper::toDomain)
        .toList();
  }

  @Override
  public List<Budget> findAllActive(UserId owner) {
    return repository.findByUserIdAndDeletedAtIsNull(owner.value()).stream()
        .map(BudgetPersistenceMapper::toDomain)
        .toList();
  }

  @Override
  public long countActive(UserId owner) {
    return repository.countByUserIdAndDeletedAtIsNull(owner.value());
  }

  /**
   * Đụng unique index (danh mục, tiền tệ) là {@code budget.duplicate}. Postgres huỷ transaction
   * hiện tại khi đó, nên lỗi phải bay lên để cả op rollback; không được bắt rồi đi tiếp.
   */
  @Override
  public boolean insertIfAbsent(UserId owner, Budget budget) {
    requireOwner(owner, budget);
    long changeSeq = sequencer.next(owner);
    try {
      return jdbc.update(
              INSERT, BudgetPersistenceMapper.insertParams(budget, changeSeq, clock.instant()))
          == 1;
    } catch (DuplicateKeyException e) {
      throw new DuplicateBudgetException(budget.getTerms().categoryId());
    }
  }

  @Override
  public void update(UserId owner, Budget budget) {
    requireOwner(owner, budget);
    BudgetJpaEntity entity =
        repository
            .findByIdAndUserId(budget.getId().value(), owner.value())
            .orElseThrow(() -> new BudgetNotFoundException(budget.getId().value()));
    BudgetPersistenceMapper.apply(budget, entity);
    entity.assignChangeSeq(sequencer.next(owner));
    repository.saveAndFlush(entity);
  }

  @Override
  public List<Sequenced<Budget>> changesSince(UserId owner, long afterSeq, int limit) {
    return repository
        .findByUserIdAndChangeSeqGreaterThanOrderByChangeSeqAsc(
            owner.value(), afterSeq, Limit.of(limit))
        .stream()
        .map(BudgetPersistenceAdapter::sequenced)
        .toList();
  }

  @Override
  public Optional<Sequenced<Budget>> findSequenced(UserId owner, BudgetId id) {
    return repository
        .findByIdAndUserId(id.value(), owner.value())
        .map(BudgetPersistenceAdapter::sequenced);
  }

  private static Sequenced<Budget> sequenced(BudgetJpaEntity entity) {
    return new Sequenced<>(BudgetPersistenceMapper.toDomain(entity), entity.getChangeSeq());
  }

  /** Hợp đồng của port out: {@code owner} truyền vào phải đúng là chủ của bản ghi (B1). */
  private static void requireOwner(UserId owner, Budget budget) {
    if (!budget.isOwnedBy(owner)) {
      throw new IllegalArgumentException("owner does not match the record being written");
    }
  }
}
