package com.uit.finance.modules.ledger.adapter.out.persistence.transaction;

import com.uit.finance.modules.ledger.adapter.out.persistence.support.Owners;
import com.uit.finance.modules.ledger.application.port.out.transaction.LoadTransactionPort;
import com.uit.finance.modules.ledger.application.port.out.transaction.SaveTransactionPort;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.modules.ledger.domain.model.transaction.Transaction;
import com.uit.finance.modules.ledger.domain.model.transaction.TransactionId;
import com.uit.finance.shared.kernel.UserId;
import com.uit.finance.shared.sync.ChangeSequencer;
import java.time.Clock;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Đọc/ghi giao dịch. FK ghép {@code (user_id, account_id)} trong V4 là lớp chặn thứ hai: dù code có
 * lỗi, DB vẫn không cho giao dịch của user này trỏ vào ví của user khác.
 */
@Component
class TransactionPersistenceAdapter implements LoadTransactionPort, SaveTransactionPort {

  private static final String INSERT =
      """
      INSERT INTO transactions (id, user_id, type, status, account_id, counter_account_id,
                                amount_minor, currency, category_id, occurred_on, occurred_at,
                                payee, note, change_seq, version, created_at, updated_at,
                                deleted_at)
      VALUES (:id, :userId, :type, :status, :accountId, :counterAccountId,
              :amountMinor, :currency, :categoryId, :occurredOn, :occurredAt,
              :payee, :note, :changeSeq, 0, :now, :now, :deletedAt)
      ON CONFLICT (id) DO NOTHING
      """;

  private final TransactionJpaRepository repository;
  private final NamedParameterJdbcTemplate jdbc;
  private final ChangeSequencer sequencer;
  private final Clock clock;

  TransactionPersistenceAdapter(
      TransactionJpaRepository repository,
      NamedParameterJdbcTemplate jdbc,
      ChangeSequencer sequencer,
      Clock clock) {
    this.repository = repository;
    this.jdbc = jdbc;
    this.sequencer = sequencer;
    this.clock = clock;
  }

  @Override
  public Optional<Transaction> find(UserId owner, TransactionId id) {
    return repository
        .findByIdAndUserId(id.value(), owner.value())
        .map(TransactionPersistenceMapper::toDomain);
  }

  @Override
  public boolean insertIfAbsent(UserId owner, Transaction transaction) {
    Owners.require(owner, transaction.getOwner());
    long changeSeq = sequencer.next(owner);
    return jdbc.update(
            INSERT,
            TransactionPersistenceMapper.insertParams(transaction, changeSeq, clock.instant()))
        == 1;
  }

  @Override
  public void update(UserId owner, Transaction transaction) {
    Owners.require(owner, transaction.getOwner());
    TransactionJpaEntity entity =
        repository
            .findByIdAndUserId(transaction.getId().value(), owner.value())
            .orElseThrow(
                () ->
                    new LedgerNotFoundException(
                        LedgerEntity.TRANSACTION, transaction.getId().value()));
    TransactionPersistenceMapper.apply(transaction, entity);
    entity.assignChangeSeq(sequencer.next(owner));
    repository.saveAndFlush(entity);
  }
}
