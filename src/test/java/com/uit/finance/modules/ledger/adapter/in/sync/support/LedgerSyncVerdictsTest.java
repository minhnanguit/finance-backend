package com.uit.finance.modules.ledger.adapter.in.sync.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.modules.ledger.domain.exception.ArchivedException;
import com.uit.finance.modules.ledger.domain.exception.EntityDeletedException;
import com.uit.finance.modules.ledger.domain.exception.ReferencePendingException;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.shared.sync.SyncOpResult;
import com.uit.finance.shared.sync.SyncOutcome;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

class LedgerSyncVerdictsTest {

  private static SyncOpResult failingWith(RuntimeException e) {
    return LedgerSyncVerdicts.guard(
        () -> {
          throw e;
        });
  }

  @Test
  @DisplayName("tham chiếu chưa tới → RETRY; đã xoá → CONFLICT; luật khác → REJECTED kèm mã")
  void mapsDomainCodes() {
    UUID id = UUID.randomUUID();

    assertThat(failingWith(new ReferencePendingException(LedgerEntity.ACCOUNT, id)))
        .isEqualTo(new SyncOpResult(SyncOutcome.RETRY, ReferencePendingException.CODE));
    assertThat(failingWith(new EntityDeletedException(LedgerEntity.TRANSACTION, id)))
        .isEqualTo(new SyncOpResult(SyncOutcome.CONFLICT, EntityDeletedException.CODE));
    assertThat(failingWith(new ArchivedException(LedgerEntity.ACCOUNT, id)))
        .isEqualTo(new SyncOpResult(SyncOutcome.REJECTED, ArchivedException.CODE));
  }

  @Test
  @DisplayName("hai lần sửa song song đụng optimistic lock → RETRY")
  void concurrentUpdateIsRetried() {
    assertThat(failingWith(new OptimisticLockingFailureException("stale")).outcome())
        .isEqualTo(SyncOutcome.RETRY);
  }

  @Test
  @DisplayName("lỗi lập trình không bị nuốt")
  void bugsPropagate() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> failingWith(new IllegalStateException("bug")))
        .isInstanceOf(IllegalStateException.class);
  }
}
