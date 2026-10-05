package com.uit.finance.modules.ledger.adapter.out.persistence.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Chỉ khai báo method có {@code userId}: kế thừa {@code Repository} trần thay vì {@code
 * JpaRepository} để không có sẵn {@code findById} không lọc chủ sở hữu (ADR-006 B1).
 */
interface AccountJpaRepository extends Repository<AccountJpaEntity, UUID> {

  Optional<AccountJpaEntity> findByIdAndUserId(UUID id, UUID userId);

  List<AccountJpaEntity> findByUserIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(UUID userId);

  AccountJpaEntity saveAndFlush(AccountJpaEntity entity);
}
