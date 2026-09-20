---
name: add-event-consumer
description: Thêm consumer RabbitMQ cho domain event trong finance-backend (queue, listener, dedup, parking lot). Dùng khi task là "lắng nghe event X", "khi user đăng ký thì làm Y", "thêm listener", "xử lý message".
---

# Thêm event consumer

Bắt buộc đọc: `.codex/rules/messaging-contracts.md`. Mẫu đầy đủ: `modules/notification/adapter/in/messaging/`.

## Trước tiên: có cần broker không?

| Tình huống | Cách đúng |
|---|---|
| Cùng process, cần đơn giản | `@ApplicationModuleListener` in-process (Modulith), không cần queue |
| Cần độ bền, retry, tách service về sau | Consumer RabbitMQ (skill này) |
| Request/response của client | **Không** dùng MQ — gọi synchronous |

## 1. Khai queue (module consumer sở hữu)

`modules/<consumer>/adapter/in/messaging/<X>Queues.java`:

```java
@Configuration(proxyBeanMethods = false)
class NotificationQueues {
  static final String USER_REGISTERED_QUEUE = "notification.user-registered";   // <consumer>.<purpose>
  static final String USER_REGISTERED_ROUTING_KEY = "identity.user.registered"; // <module>.<aggregate>.<action>

  @Bean Declarables notificationQueueDeclarables() {
    return ConsumerQueues.forEvent(USER_REGISTERED_QUEUE, USER_REGISTERED_ROUTING_KEY);
  }
}
```

`ConsumerQueues.forEvent` tự tạo: quorum queue + binding vào `finance.events` + queue `<queue>.parked` + binding DLX. **Đừng tự dựng `Queue`/`Binding`.**

## 2. Payload cục bộ

Định nghĩa record payload **của riêng module consumer** (mẫu `UserRegisteredPayload`). **Không import class event của module producer** — đó là vi phạm ranh giới module và `ModularityTest` sẽ fail.

## 3. Listener

```java
@Component
class UserRegisteredListener {
  static final String CONSUMER = NotificationQueues.USER_REGISTERED_QUEUE;

  @RabbitListener(queues = NotificationQueues.USER_REGISTERED_QUEUE)
  void onUserRegistered(Message message) {
    String json = new String(message.getBody(), StandardCharsets.UTF_8);
    EventEnvelope<UserRegisteredPayload> event = inboundEvents.decode(json, UserRegisteredPayload.class);
    deduplicator.executeOnce(event.eventId(), CONSUMER, () -> useCase.send(new Command(...)));
  }
}
```

- Listener package-private, đặt ở `adapter/in/messaging`.
- **Bắt buộc** bọc `EventDeduplicator.executeOnce` — at-least-once delivery nên duplicate là chuyện bình thường.
- Listener chỉ decode + gọi use case. Logic ở `application/service`.
- **Không** `@Transactional` trên listener (vi phạm luật 8) — `executeOnce` đã mở transaction.
- **Để exception bay lên**: container retry backoff (1s ×2, max 5 lần, cap 30s) rồi park vào `<queue>.parked`. Nuốt exception = mất message âm thầm.

## 4. Nếu bạn là bên phát event

- Event là `record implements DomainEvent` trong `domain/event/`, `type()` = routing key.
- Publish trong use case: `events.publishAll(aggregate.pullDomainEvents())`. Outbox + externalize tự động qua `EventExternalizationConfig`; **không** thêm annotation broker vào domain.
- Muốn module khác consume in-process → `domain/event/package-info.java` có `@NamedInterface("events")` và module kia khai `allowedDependencies = {"<module>::events"}`.

## 5. Test

- Unit: use case với fake port.
- Integration: mẫu `UserRegisteredEventIT` — gọi HTTP → outbox → RabbitMQ → consumer, chờ bằng `awaitility`.

## Checklist

- [ ] Queue khai bằng `ConsumerQueues.forEvent`, tên đúng `<consumer>.<purpose>`
- [ ] Payload record cục bộ, không import class module khác
- [ ] `EventDeduplicator.executeOnce(eventId, CONSUMER, handler)`
- [ ] Không nuốt exception, không `@Transactional` trên listener
- [ ] `make test itest` xanh
- [ ] Đổi payload không tương thích → tăng `schemaVersion()` và nêu kế hoạch chạy song song 2 version
