# Rule — Hợp đồng messaging (RabbitMQ + outbox)

Domain và application **không biết RabbitMQ tồn tại**. Mọi thứ broker nằm ở `shared/messaging` và `adapter/*/messaging`.

## Đường đi

```
use case @Transactional
  └─ DomainEventPublisher.publishAll(aggregate.pullDomainEvents())
       ├─ listener in-process (@ApplicationModuleListener)
       └─ Modulith ghi vào event_publication CÙNG transaction
            └─ sau commit → EventEnvelope → exchange finance.events (at-least-once)
                 └─ queue của consumer → EventDeduplicator.executeOnce
```

## Topology (`FinanceExchanges`)

| Thành phần | Giá trị |
|---|---|
| `finance.events` | topic — pub/sub domain event |
| `finance.commands` | direct — task queue (push, export, aggregate) |
| `finance.dlx` | dead letter |
| Routing key | `<module>.<aggregate>.<action>` → `identity.user.registered` |
| Queue | `<consumer>.<purpose>` → `notification.user-registered` |
| Parking lot | `<queue>.parked` |

Queue luôn tạo qua `ConsumerQueues.forEvent(queueName, routingKey)`: quorum queue + binding + parked queue + binding DLX. **Đừng khai báo `Queue` thủ công.**

## Luật

1. **Consumer sở hữu queue của mình.** Module consume thì module đó khai `@Bean Declarables` (mẫu `NotificationQueues`).
2. **Không import class event của module khác.** Consumer định nghĩa payload record cục bộ của riêng nó (mẫu `UserRegisteredPayload`) và decode bằng `InboundEvents.decode(json, Payload.class)`.
3. **Idempotent bắt buộc**: bọc handler trong `deduplicator.executeOnce(event.eventId(), CONSUMER, () -> ...)`. Mark + handler chạy trong một transaction; handler lỗi → rollback cả mark → broker redeliver.
4. **Event mang giá trị đơn giản** (UUID, String, Instant, long), không mang value object của domain. Event là integration contract.
5. `type()` = routing key. Đổi payload không tương thích → tăng `schemaVersion()` và chạy song song hai version. Thêm field thì giữ nguyên version.
6. Lỗi thì **để exception bay lên** — container retry (backoff 1s ×2, tối đa 5 lần, cap 30s) rồi đẩy vào `<queue>.parked`. Đừng nuốt exception trong listener.
7. `default-requeue-rejected=false`, ack mode `AUTO` (container ack sau khi listener xong). Đừng đổi sang broker auto-ack.
8. Header chuẩn: `x-event-id`, `x-event-type`, `x-schema-version`. Envelope có `eventId, type, schemaVersion, occurredAt, traceId, userId, payload`.

## Cái gì KHÔNG qua RabbitMQ (ADR-003, ARCHITECTURE §5.4)

- Request/response của client. Sync push & pull đều **synchronous**.
- Event sourcing, replay lịch sử, analytics stream → cần thì thêm Kafka song song, không ép RabbitMQ.
