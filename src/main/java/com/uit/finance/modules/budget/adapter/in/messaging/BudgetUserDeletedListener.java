package com.uit.finance.modules.budget.adapter.in.messaging;

import com.uit.finance.modules.budget.application.port.in.PurgeUserBudgetsUseCase;
import com.uit.finance.shared.kernel.UserId;
import com.uit.finance.shared.messaging.EventDeduplicator;
import com.uit.finance.shared.messaging.EventEnvelope;
import com.uit.finance.shared.messaging.InboundEvents;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Tài khoản đã xoá: xoá thật mọi ngân sách của user (ADR-008 A7). Queue riêng, độc lập với ledger:
 * không có FK giữa hai bên nên thứ tự xử lý không quan trọng. Lỗi thì để broker thử lại rồi park.
 */
@Component
class BudgetUserDeletedListener {

  static final String CONSUMER = BudgetQueues.USER_DELETED_QUEUE;

  private static final Logger log = LoggerFactory.getLogger(BudgetUserDeletedListener.class);

  private final InboundEvents inboundEvents;
  private final EventDeduplicator deduplicator;
  private final PurgeUserBudgetsUseCase purge;

  BudgetUserDeletedListener(
      InboundEvents inboundEvents, EventDeduplicator deduplicator, PurgeUserBudgetsUseCase purge) {
    this.inboundEvents = inboundEvents;
    this.deduplicator = deduplicator;
    this.purge = purge;
  }

  @RabbitListener(queues = BudgetQueues.USER_DELETED_QUEUE)
  void onUserDeleted(Message message) {
    String json = new String(message.getBody(), StandardCharsets.UTF_8);
    EventEnvelope<UserDeletedPayload> event = inboundEvents.decode(json, UserDeletedPayload.class);
    UserId owner = new UserId(event.payload().userId());
    deduplicator.executeOnce(
        event.eventId(),
        CONSUMER,
        () -> log.info("budgets purged for deleted user={} rows={}", owner, purge.purge(owner)));
  }
}
