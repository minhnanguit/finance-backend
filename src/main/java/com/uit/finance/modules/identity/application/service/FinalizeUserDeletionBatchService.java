package com.uit.finance.modules.identity.application.service;

import com.uit.finance.modules.identity.application.port.out.LoadUserPort;
import com.uit.finance.modules.identity.application.port.out.SaveUserPort;
import com.uit.finance.modules.identity.domain.model.User;
import com.uit.finance.shared.kernel.DomainEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Một lô trong một transaction: khoá các tài khoản đang chờ ({@code SKIP LOCKED}), chuyển sang
 * {@code DELETED} và ghi {@code identity.user.deleted} vào outbox cùng lúc. Commit rồi event mới
 * được gửi đi.
 */
@Service
@Transactional
class FinalizeUserDeletionBatchService {

  private final LoadUserPort loadUser;
  private final SaveUserPort saveUser;
  private final DomainEventPublisher events;
  private final Clock clock;

  FinalizeUserDeletionBatchService(
      LoadUserPort loadUser, SaveUserPort saveUser, DomainEventPublisher events, Clock clock) {
    this.loadUser = loadUser;
    this.saveUser = saveUser;
    this.events = events;
    this.clock = clock;
  }

  /**
   * @return số tài khoản của lô này
   */
  int finalizeBatch(Instant requestedBefore, int limit) {
    Instant now = clock.instant();
    List<User> pending = loadUser.lockPendingDeletions(requestedBefore, limit);
    for (User user : pending) {
      User deleted = user.completeDeletion(now);
      saveUser.update(deleted);
      events.publishAll(deleted.pullDomainEvents());
    }
    return pending.size();
  }
}
