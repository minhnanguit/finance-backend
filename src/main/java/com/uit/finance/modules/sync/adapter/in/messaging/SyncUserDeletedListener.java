package com.uit.finance.modules.sync.adapter.in.messaging;

import com.uit.finance.modules.sync.application.port.in.PurgeUserSyncDataUseCase;
import com.uit.finance.shared.kernel.UserId;
import com.uit.finance.shared.messaging.EventDeduplicator;
import com.uit.finance.shared.messaging.EventEnvelope;
import com.uit.finance.shared.messaging.InboundEvents;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Tài khoản đã xoá: xoá nhật ký op và trạng thái sync của user (ADR-008 A7). */
@Component
class SyncUserDeletedListener {

  static final String CONSUMER = SyncQueues.USER_DELETED_QUEUE;

  private final InboundEvents inboundEvents;
  private final EventDeduplicator deduplicator;
  private final PurgeUserSyncDataUseCase purge;

  SyncUserDeletedListener(
      InboundEvents inboundEvents, EventDeduplicator deduplicator, PurgeUserSyncDataUseCase purge) {
    this.inboundEvents = inboundEvents;
    this.deduplicator = deduplicator;
    this.purge = purge;
  }

  @RabbitListener(queues = SyncQueues.USER_DELETED_QUEUE)
  void onUserDeleted(Message message) {
    String json = new String(message.getBody(), StandardCharsets.UTF_8);
    EventEnvelope<UserDeletedPayload> event = inboundEvents.decode(json, UserDeletedPayload.class);
    deduplicator.executeOnce(
        event.eventId(), CONSUMER, () -> purge.purge(new UserId(event.payload().userId())));
  }
}
