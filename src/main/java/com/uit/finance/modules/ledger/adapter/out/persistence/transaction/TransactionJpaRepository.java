package com.uit.finance.modules.ledger.adapter.out.persistence.transaction;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Chỉ có method lọc theo {@code userId} (ADR-006 B1); xem {@code AccountJpaRepository}. */
interface TransactionJpaRepository extends Repository<TransactionJpaEntity, UUID> {

  Optional<TransactionJpaEntity> findByIdAndUserId(UUID id, UUID userId);

  TransactionJpaEntity saveAndFlush(TransactionJpaEntity entity);
}
