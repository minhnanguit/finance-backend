package com.uit.finance.modules.identity.application.service;

import com.uit.finance.modules.identity.application.port.in.RemoveIdentityProviderAccountsUseCase;
import com.uit.finance.modules.identity.application.port.out.IdentityProviderAccountsPort;
import com.uit.finance.modules.identity.application.port.out.IdentityProviderRemovalsPort;
import com.uit.finance.modules.identity.application.port.out.IdentityProviderRemovalsPort.PendingRemoval;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Hàng đợi bền + thử lại không giới hạn (ADR-008 A8), thay cho retry ngắn của RabbitMQ: Keycloak có
 * thể tắt hàng giờ để nâng cấp, secret có thể cấu hình sai — tài khoản vẫn phải bị xoá khi mọi thứ
 * ổn lại.
 *
 * <p>Cố ý không {@code @Transactional}: lời gọi HTTP không được giữ connection DB. Đọc hàng đợi và
 * mỗi lần đánh dấu là một câu SQL tự commit. Nhiều instance cùng chạy có thể gọi xoá trùng một tài
 * khoản; Keycloak trả 404 cho lần sau, adapter coi là xong.
 */
@Service
class RemoveIdentityProviderAccountsService implements RemoveIdentityProviderAccountsUseCase {

  static final int BATCH_SIZE = 50;

  /** Chặn vòng lặp nếu một lô không đánh dấu được (không bao giờ xảy ra nếu DB đúng). */
  static final int MAX_BATCHES_PER_RUN = 20;

  /** Còn nợ lâu hơn chừng này thì log ERROR để cảnh báo: "xoá trong thời hạn hợp lý" (ND13). */
  static final Duration OVERDUE = Duration.ofHours(1);

  private static final Logger log =
      LoggerFactory.getLogger(RemoveIdentityProviderAccountsService.class);

  private final IdentityProviderRemovalsPort removals;
  private final IdentityProviderAccountsPort identityProvider;
  private final Clock clock;
  private final AtomicBoolean disabledReported = new AtomicBoolean();

  RemoveIdentityProviderAccountsService(
      IdentityProviderRemovalsPort removals,
      IdentityProviderAccountsPort identityProvider,
      Clock clock) {
    this.removals = removals;
    this.identityProvider = identityProvider;
    this.clock = clock;
  }

  @Override
  public int removePending() {
    int total = 0;
    for (int round = 0; round < MAX_BATCHES_PER_RUN; round++) {
      List<PendingRemoval> batch = removals.pending(BATCH_SIZE);
      if (batch.isEmpty()) {
        break;
      }
      if (!identityProvider.enabled()) {
        // Không đánh dấu gì: bật client lên là làm bù hết.
        if (disabledReported.compareAndSet(false, true)) {
          log.warn(
              "identity provider client disabled: {}+ account(s) wait for removal", batch.size());
        }
        break;
      }
      int removed = removeBatch(batch);
      total += removed;
      if (removed < batch.size() || batch.size() < BATCH_SIZE) {
        break;
      }
    }
    if (total > 0) {
      log.info("identity provider accounts removed count={}", total);
    }
    return total;
  }

  private int removeBatch(List<PendingRemoval> batch) {
    int removed = 0;
    for (PendingRemoval pending : batch) {
      try {
        identityProvider.deleteAccount(pending.subject());
      } catch (RuntimeException e) {
        // IdP đang lỗi: dừng cả lô, không dội thêm request; lần chạy sau thử lại từ việc cũ nhất.
        reportFailure(batch.getFirst(), batch.size() - removed, e);
        return removed;
      }
      removals.markRemoved(pending.userId(), clock.instant());
      removed++;
    }
    return removed;
  }

  private void reportFailure(PendingRemoval oldest, int waiting, RuntimeException e) {
    Duration age = Duration.between(oldest.deletionRequestedAt(), clock.instant());
    if (age.compareTo(OVERDUE) >= 0) {
      log.error(
          "identity provider removal overdue: oldest user={} waiting {}m, pending>={}, reason={}",
          oldest.userId(),
          age.toMinutes(),
          waiting,
          e.toString());
    } else {
      log.warn(
          "identity provider removal failed, retrying later: pending>={}, reason={}",
          waiting,
          e.toString());
    }
  }
}
