package com.uit.finance.modules.recurring.adapter.in.messaging;

import com.uit.finance.modules.recurring.application.port.in.PurgeUserRecurringsUseCase;
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
 * Tài khoản đã xoá: xoá thật mọi khoản định kỳ của user (ADR-008 A7). Queue riêng, độc lập với
 * ledger/budget: không có FK giữa các bên nên thứ tự xử lý không quan trọng. Lỗi thì để broker thử
 * lại rồi park.
 */
@Component
class RecurringUserDeletedListener {

  static final String CONSUMER = RecurringQueues.USER_DELETED_QUEUE;

  private static final Logger log = LoggerFactory.getLogger(RecurringUserDeletedListener.class);

  private final InboundEvents inboundEvents;
  private final EventDeduplicator deduplicator;
  private final PurgeUserRecurringsUseCase purge;

  RecurringUserDeletedListener(
      InboundEvents inboundEvents,
      EventDeduplicator deduplicator,
      PurgeUserRecurringsUseCase purge) {
    this.inboundEvents = inboundEvents;
    this.deduplicator = deduplicator;
    this.purge = purge;
  }

  @RabbitListener(queues = RecurringQueues.USER_DELETED_QUEUE)
  void onUserDeleted(Message message) {
    String json = new String(message.getBody(), StandardCharsets.UTF_8);
    EventEnvelope<UserDeletedPayload> event = inboundEvents.decode(json, UserDeletedPayload.class);
    UserId owner = new UserId(event.payload().userId());
    deduplicator.executeOnce(
        event.eventId(),
        CONSUMER,
        () -> log.info("recurrings purged for deleted user={} rows={}", owner, purge.purge(owner)));
  }
}
