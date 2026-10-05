package com.uit.finance.modules.ledger.adapter.out.persistence.category;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Chỉ có method lọc theo {@code userId} (ADR-006 B1); xem {@code AccountJpaRepository}. */
interface CategoryJpaRepository extends Repository<CategoryJpaEntity, UUID> {

  Optional<CategoryJpaEntity> findByIdAndUserId(UUID id, UUID userId);

  @Query("select c.id from CategoryJpaEntity c where c.userId = :userId and c.id in :ids")
  List<UUID> findExistingIds(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);

  CategoryJpaEntity saveAndFlush(CategoryJpaEntity entity);
}
