package com.uit.finance.modules.ledger.adapter.out.persistence.transaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.repository.Repository;

/** Chỉ có method lọc theo {@code userId} (ADR-006 B1); xem {@code AccountJpaRepository}. */
interface TransactionJpaRepository extends Repository<TransactionJpaEntity, UUID> {

  Optional<TransactionJpaEntity> findByIdAndUserId(UUID id, UUID userId);

  /** Dùng index {@code (user_id, change_seq)}. */
  List<TransactionJpaEntity> findByUserIdAndChangeSeqGreaterThanOrderByChangeSeqAsc(
      UUID userId, long afterSeq, Limit limit);

  TransactionJpaEntity saveAndFlush(TransactionJpaEntity entity);
}
