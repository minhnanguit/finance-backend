package com.uit.finance.modules.ledger.adapter.out.persistence.account;

import com.uit.finance.modules.ledger.adapter.out.persistence.support.Owners;
import com.uit.finance.modules.ledger.application.port.out.account.LoadAccountChangesPort;
import com.uit.finance.modules.ledger.application.port.out.account.LoadAccountPort;
import com.uit.finance.modules.ledger.application.port.out.account.SaveAccountPort;
import com.uit.finance.modules.ledger.application.port.out.shared.Sequenced;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.modules.ledger.domain.model.account.Account;
import com.uit.finance.modules.ledger.domain.model.account.AccountId;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.shared.kernel.UserId;
import com.uit.finance.shared.sync.ChangeSequencer;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Đọc/ghi ví. Mọi đường đọc và ghi đều lọc {@code user_id} (ADR-006 B1), mọi lần ghi đều lấy {@code
 * change_seq} mới trong cùng transaction (ADR-002 §2).
 */
@Component
class AccountPersistenceAdapter
    implements LoadAccountPort, SaveAccountPort, LoadAccountChangesPort {

  private static final String INSERT =
      """
      INSERT INTO accounts (id, user_id, name, type, currency, opening_balance_minor, sort_order,
                            archived_at, change_seq, version, created_at, updated_at, deleted_at)
      VALUES (:id, :userId, :name, :type, :currency, :openingBalanceMinor, :sortOrder,
              :archivedAt, :changeSeq, 0, :now, :now, :deletedAt)
      ON CONFLICT (id) DO NOTHING
      """;

  private final AccountJpaRepository repository;
  private final NamedParameterJdbcTemplate jdbc;
  private final ChangeSequencer sequencer;
  private final Clock clock;

  AccountPersistenceAdapter(
      AccountJpaRepository repository,
      NamedParameterJdbcTemplate jdbc,
      ChangeSequencer sequencer,
      Clock clock) {
    this.repository = repository;
    this.jdbc = jdbc;
    this.sequencer = sequencer;
    this.clock = clock;
  }

  @Override
  public Optional<Account> find(UserId owner, AccountId id) {
    return repository
        .findByIdAndUserId(id.value(), owner.value())
        .map(AccountPersistenceMapper::toDomain);
  }

  @Override
  public List<Account> listAccounts(UserId owner) {
    return repository.findByUserIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(owner.value()).stream()
        .map(AccountPersistenceMapper::toDomain)
        .toList();
  }

  @Override
  public boolean insertIfAbsent(UserId owner, Account account) {
    Owners.require(owner, account.getOwner());
    long changeSeq = sequencer.next(owner);
    return jdbc.update(
            INSERT, AccountPersistenceMapper.insertParams(account, changeSeq, clock.instant()))
        == 1;
  }

  @Override
  public void update(UserId owner, Account account) {
    Owners.require(owner, account.getOwner());
    AccountJpaEntity entity =
        repository
            .findByIdAndUserId(account.getId().value(), owner.value())
            .orElseThrow(
                () -> new LedgerNotFoundException(LedgerEntity.ACCOUNT, account.getId().value()));
    AccountPersistenceMapper.apply(account, entity);
    entity.assignChangeSeq(sequencer.next(owner));
    repository.saveAndFlush(entity);
  }

  @Override
  public List<Sequenced<Account>> changesSince(UserId owner, long afterSeq, int limit) {
    return repository
        .findByUserIdAndChangeSeqGreaterThanOrderByChangeSeqAsc(
            owner.value(), afterSeq, Limit.of(limit))
        .stream()
        .map(
            entity ->
                new Sequenced<>(AccountPersistenceMapper.toDomain(entity), entity.getChangeSeq()))
        .toList();
  }

  @Override
  public Optional<Sequenced<Account>> findSequenced(UserId owner, AccountId id) {
    return repository
        .findByIdAndUserId(id.value(), owner.value())
        .map(
            entity ->
                new Sequenced<>(AccountPersistenceMapper.toDomain(entity), entity.getChangeSeq()));
  }
}
