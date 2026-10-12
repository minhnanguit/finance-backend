package com.uit.finance.modules.ledger.adapter.out.persistence.category;

import com.uit.finance.modules.ledger.adapter.out.persistence.support.Owners;
import com.uit.finance.modules.ledger.application.port.out.category.LoadCategoryChangesPort;
import com.uit.finance.modules.ledger.application.port.out.category.LoadCategoryPort;
import com.uit.finance.modules.ledger.application.port.out.category.SaveCategoryPort;
import com.uit.finance.modules.ledger.application.port.out.category.SeedCategoriesPort;
import com.uit.finance.modules.ledger.application.port.out.shared.Sequenced;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.shared.kernel.UserId;
import com.uit.finance.shared.sync.ChangeSequencer;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Đọc/ghi danh mục, cùng luật lọc {@code user_id} và {@code change_seq} như ví. */
@Component
class CategoryPersistenceAdapter
    implements LoadCategoryPort, SaveCategoryPort, SeedCategoriesPort, LoadCategoryChangesPort {

  private static final String INSERT =
      """
      INSERT INTO categories (id, user_id, kind, name, parent_id, icon, color, template_key,
                              archived_at, change_seq, version, created_at, updated_at, deleted_at)
      VALUES (:id, :userId, :kind, :name, :parentId, :icon, :color, :templateKey,
              :archivedAt, :changeSeq, 0, :now, :now, :deletedAt)
      ON CONFLICT (id) DO NOTHING
      """;

  private final CategoryJpaRepository repository;
  private final NamedParameterJdbcTemplate jdbc;
  private final ChangeSequencer sequencer;
  private final Clock clock;

  CategoryPersistenceAdapter(
      CategoryJpaRepository repository,
      NamedParameterJdbcTemplate jdbc,
      ChangeSequencer sequencer,
      Clock clock) {
    this.repository = repository;
    this.jdbc = jdbc;
    this.sequencer = sequencer;
    this.clock = clock;
  }

  @Override
  public Optional<Category> find(UserId owner, CategoryId id) {
    return repository
        .findByIdAndUserId(id.value(), owner.value())
        .map(CategoryPersistenceMapper::toDomain);
  }

  @Override
  public boolean insertIfAbsent(UserId owner, Category category) {
    Owners.require(owner, category.getOwner());
    long changeSeq = sequencer.next(owner);
    return jdbc.update(
            INSERT, CategoryPersistenceMapper.insertParams(category, changeSeq, clock.instant()))
        == 1;
  }

  @Override
  public void update(UserId owner, Category category) {
    Owners.require(owner, category.getOwner());
    CategoryJpaEntity entity =
        repository
            .findByIdAndUserId(category.getId().value(), owner.value())
            .orElseThrow(
                () -> new LedgerNotFoundException(LedgerEntity.CATEGORY, category.getId().value()));
    CategoryPersistenceMapper.apply(category, entity);
    entity.assignChangeSeq(sequencer.next(owner));
    repository.saveAndFlush(entity);
  }

  /**
   * Khoá dãy số của user trước, rồi mới xem dòng nào còn thiếu, để chỉ lấy đúng số {@code
   * change_seq} cần dùng: dãy không có lỗ dù 2 máy cùng sync lần đầu. Ghi bằng một JDBC batch, một
   * lượt đi về DB.
   */
  @Override
  public int insertAllIfAbsent(UserId owner, List<Category> categories) {
    categories.forEach(category -> Owners.require(owner, category.getOwner()));
    if (categories.isEmpty()) {
      return 0;
    }
    sequencer.lock(owner);
    Set<UUID> existing =
        new HashSet<>(
            repository.findExistingIds(
                owner.value(), categories.stream().map(c -> c.getId().value()).toList()));
    List<Category> missing =
        categories.stream().filter(c -> !existing.contains(c.getId().value())).toList();
    if (missing.isEmpty()) {
      return 0;
    }

    long changeSeq = sequencer.reserve(owner, missing.size());
    Instant now = clock.instant();
    MapSqlParameterSource[] batch = new MapSqlParameterSource[missing.size()];
    for (int i = 0; i < batch.length; i++) {
      batch[i] = CategoryPersistenceMapper.insertParams(missing.get(i), changeSeq + i, now);
    }
    int[] counts = jdbc.batchUpdate(INSERT, batch);
    // reWriteBatchedInserts của pgjdbc trả SUCCESS_NO_INFO: khi đó không biết dòng nào được ghi.
    if (Arrays.stream(counts).anyMatch(count -> count == Statement.SUCCESS_NO_INFO)) {
      throw new IllegalStateException("JDBC driver did not report per-row insert counts");
    }
    return Arrays.stream(counts).sum();
  }

  @Override
  public List<Sequenced<Category>> changesSince(UserId owner, long afterSeq, int limit) {
    return repository
        .findByUserIdAndChangeSeqGreaterThanOrderByChangeSeqAsc(
            owner.value(), afterSeq, Limit.of(limit))
        .stream()
        .map(
            entity ->
                new Sequenced<>(CategoryPersistenceMapper.toDomain(entity), entity.getChangeSeq()))
        .toList();
  }

  @Override
  public Optional<Sequenced<Category>> findSequenced(UserId owner, CategoryId id) {
    return repository
        .findByIdAndUserId(id.value(), owner.value())
        .map(
            entity ->
                new Sequenced<>(CategoryPersistenceMapper.toDomain(entity), entity.getChangeSeq()));
  }
}
