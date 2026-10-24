package com.uit.finance.modules.budget.adapter.out.persistence;

import com.uit.finance.modules.budget.domain.model.Budget;
import com.uit.finance.modules.budget.domain.model.BudgetAmount;
import com.uit.finance.modules.budget.domain.model.BudgetId;
import com.uit.finance.modules.budget.domain.model.BudgetMonth;
import com.uit.finance.modules.budget.domain.model.BudgetTerms;
import com.uit.finance.shared.kernel.UserId;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

final class BudgetPersistenceMapper {

  private BudgetPersistenceMapper() {}

  static Budget toDomain(BudgetJpaEntity entity) {
    return Budget.rehydrate(
        new BudgetId(entity.getId()),
        new UserId(entity.getUserId()),
        new BudgetTerms(
            entity.getCategoryId(),
            Currency.getInstance(entity.getCurrency()),
            new BudgetAmount(entity.getAmountMinor()),
            new BudgetMonth(entity.getStartMonth())),
        entity.getDeletedAt());
  }

  static void apply(Budget budget, BudgetJpaEntity entity) {
    entity.applyAmount(budget.getTerms().amount().minor());
    if (budget.getDeletedAt() != null && !entity.isDeleted()) {
      entity.markDeleted(budget.getDeletedAt());
    }
  }

  /**
   * Tham số cho {@code BudgetPersistenceAdapter.INSERT}. Khai báo kiểu SQL cho từng tham số để giá
   * trị {@code null} vẫn bind đúng kiểu cột (uuid, timestamptz).
   */
  static MapSqlParameterSource insertParams(Budget budget, long changeSeq, Instant now) {
    BudgetTerms terms = budget.getTerms();
    return new MapSqlParameterSource()
        .addValue("id", budget.getId().value(), Types.OTHER)
        .addValue("userId", budget.getOwner().value(), Types.OTHER)
        .addValue("categoryId", terms.categoryId(), Types.OTHER)
        .addValue("currency", terms.currency().getCurrencyCode(), Types.VARCHAR)
        .addValue("amountMinor", terms.amount().minor(), Types.BIGINT)
        .addValue("startMonth", terms.startMonth().firstDay(), Types.DATE)
        .addValue("changeSeq", changeSeq, Types.BIGINT)
        .addValue("now", utc(now), Types.TIMESTAMP_WITH_TIMEZONE)
        .addValue("deletedAt", utc(budget.getDeletedAt()), Types.TIMESTAMP_WITH_TIMEZONE);
  }

  /** {@link Instant} đi qua {@code OffsetDateTime} UTC vì JDBC driver không bind thẳng Instant. */
  private static @Nullable OffsetDateTime utc(@Nullable Instant value) {
    return value == null ? null : value.atOffset(ZoneOffset.UTC);
  }
}
