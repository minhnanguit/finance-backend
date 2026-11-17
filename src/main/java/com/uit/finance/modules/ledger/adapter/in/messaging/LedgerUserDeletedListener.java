package com.uit.finance.modules.ledger.adapter.in.messaging;

import com.uit.finance.modules.ledger.application.port.in.maintenance.PurgeUserLedgerUseCase;
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

/** Tài khoản đã xoá: xoá thật cả sổ của user (ADR-008 A7). Lỗi thì để broker thử lại rồi park. */
@Component
class LedgerUserDeletedListener {

  static final String CONSUMER = LedgerQueues.USER_DELETED_QUEUE;
  private static final Logger log = LoggerFactory.getLogger(LedgerUserDeletedListener.class);

  private final InboundEvents inboundEvents;
  private final EventDeduplicator deduplicator;
  private final PurgeUserLedgerUseCase purge;

  LedgerUserDeletedListener(
      InboundEvents inboundEvents, EventDeduplicator deduplicator, PurgeUserLedgerUseCase purge) {
    this.inboundEvents = inboundEvents;
    this.deduplicator = deduplicator;
    this.purge = purge;
  }

  @RabbitListener(queues = LedgerQueues.USER_DELETED_QUEUE)
  void onUserDeleted(Message message) {
    String json = new String(message.getBody(), StandardCharsets.UTF_8);
    EventEnvelope<UserDeletedPayload> event = inboundEvents.decode(json, UserDeletedPayload.class);
    UserId owner = new UserId(event.payload().userId());
    deduplicator.executeOnce(
        event.eventId(),
        CONSUMER,
        () -> log.info("ledger purged for deleted user={} rows={}", owner, purge.purge(owner)));
  }
}
