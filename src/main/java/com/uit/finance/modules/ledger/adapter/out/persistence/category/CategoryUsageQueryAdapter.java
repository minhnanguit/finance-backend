package com.uit.finance.modules.ledger.adapter.out.persistence.category;

import com.uit.finance.modules.ledger.application.port.out.category.CategoryUsagePort;
import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import com.uit.finance.shared.kernel.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Đếm và kiểm tra bằng SQL, không load entity. */
@Component
class CategoryUsageQueryAdapter implements CategoryUsagePort {

  private final JdbcClient jdbc;

  CategoryUsageQueryAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public long countCategories(UserId owner) {
    return jdbc.sql("SELECT count(*) FROM categories WHERE user_id = :owner AND deleted_at IS NULL")
        .param("owner", owner.value())
        .query(Long.class)
        .single();
  }

  /** Dùng index {@code (user_id, category_id, occurred_on)}. */
  @Override
  public boolean isReferenced(UserId owner, CategoryId id) {
    return jdbc.sql(
            """
            SELECT EXISTS (
              SELECT 1 FROM transactions
              WHERE user_id = :owner AND category_id = :id AND deleted_at IS NULL)
            """)
        .param("owner", owner.value())
        .param("id", id.value())
        .query(Boolean.class)
        .single();
  }

  /** Dùng index {@code (user_id, parent_id)}. */
  @Override
  public boolean hasActiveChildren(UserId owner, CategoryId id) {
    return jdbc.sql(
            """
            SELECT EXISTS (
              SELECT 1 FROM categories
              WHERE user_id = :owner AND parent_id = :id AND deleted_at IS NULL)
            """)
        .param("owner", owner.value())
        .param("id", id.value())
        .query(Boolean.class)
        .single();
  }
}
