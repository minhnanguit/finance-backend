package com.mosaicglobal.finance.modules.notification.adapter.in.messaging;

import com.mosaicglobal.finance.modules.notification.application.port.in.SendWelcomeNotificationUseCase;
import com.mosaicglobal.finance.modules.notification.application.port.in.SendWelcomeNotificationUseCase.SendWelcomeNotificationCommand;
import com.mosaicglobal.finance.shared.messaging.EventDeduplicator;
import com.mosaicglobal.finance.shared.messaging.EventEnvelope;
import com.mosaicglobal.finance.shared.messaging.InboundEvents;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code identity.user.registered} and sends the welcome message exactly once per event.
 * Failures propagate so the container retries with back-off and finally parks the message.
 */
@Component
class UserRegisteredListener {

  static final String CONSUMER = NotificationQueues.USER_REGISTERED_QUEUE;

  private final InboundEvents inboundEvents;
  private final EventDeduplicator deduplicator;
  private final SendWelcomeNotificationUseCase sendWelcome;

  UserRegisteredListener(
      InboundEvents inboundEvents,
      EventDeduplicator deduplicator,
      SendWelcomeNotificationUseCase sendWelcome) {
    this.inboundEvents = inboundEvents;
    this.deduplicator = deduplicator;
    this.sendWelcome = sendWelcome;
  }

  @RabbitListener(queues = NotificationQueues.USER_REGISTERED_QUEUE)
  void onUserRegistered(Message message) {
    String json = new String(message.getBody(), StandardCharsets.UTF_8);
    EventEnvelope<UserRegisteredPayload> event =
        inboundEvents.decode(json, UserRegisteredPayload.class);
    UserRegisteredPayload payload = event.payload();
    deduplicator.executeOnce(
        event.eventId(),
        CONSUMER,
        () ->
            sendWelcome.send(
                new SendWelcomeNotificationCommand(
                    payload.userId(), payload.email(), payload.displayName())));
  }
}
