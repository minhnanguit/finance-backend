package com.uit.finance.modules.budget.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.repository.Repository;

/** Chỉ có method lọc theo {@code userId} (ADR-006 B1); không có {@code findById} trần. */
interface BudgetJpaRepository extends Repository<BudgetJpaEntity, UUID> {

  Optional<BudgetJpaEntity> findByIdAndUserId(UUID id, UUID userId);

  /** Dùng unique index {@code ux_budgets_user_category_currency}. */
  Optional<BudgetJpaEntity> findByUserIdAndCategoryIdAndCurrencyAndDeletedAtIsNull(
      UUID userId, UUID categoryId, String currency);

  /** Dùng index {@code ix_budgets_user_category}. */
  List<BudgetJpaEntity> findByUserIdAndCategoryIdAndDeletedAtIsNull(UUID userId, UUID categoryId);

  List<BudgetJpaEntity> findByUserIdAndDeletedAtIsNull(UUID userId);

  long countByUserIdAndDeletedAtIsNull(UUID userId);

  /** Dùng index {@code (user_id, change_seq)}. */
  List<BudgetJpaEntity> findByUserIdAndChangeSeqGreaterThanOrderByChangeSeqAsc(
      UUID userId, long afterSeq, Limit limit);

  BudgetJpaEntity saveAndFlush(BudgetJpaEntity entity);
}
