---
name: add-module
description: Tạo module nghiệp vụ mới (Spring Modulith application module) trong finance-backend. Dùng khi task là "thêm module transactions/budget/reporting", "tách domain mới", "scaffold bounded context".
---

# Thêm module nghiệp vụ mới

Nguồn chuẩn trong repo: `docs/module-template.md`. Skill này là quy trình thao tác; khi mâu thuẫn, `docs/module-template.md` và `ARCHITECTURE.md` thắng.

Mẫu để copy hình dạng: `modules/identity` (producer, vào bằng HTTP) và `modules/notification` (consumer, vào bằng messaging).

## 1. Skeleton

```
src/main/java/com/mosaicglobal/finance/modules/<name>/
├─ package-info.java
├─ domain/model/ · domain/event/ · domain/exception/ · (domain/service/)
├─ application/port/in/ · application/port/out/ · application/service/
└─ adapter/in/web/ · (adapter/in/messaging/) · adapter/out/persistence/ · (adapter/config/)
```

`package-info.java`:

```java
@org.springframework.modulith.ApplicationModule(displayName = "<Name>", allowedDependencies = {})
package com.mosaicglobal.finance.modules.<name>;
```

`allowedDependencies = {}` nghĩa là chỉ được dùng `shared`. Cần event của module khác → `{"identity::events"}`.
Module muốn expose event cho người khác → `domain/event/package-info.java` với `@NamedInterface("events")`.

**Detection strategy là `explicitly-annotated`** — thiếu annotation thì Modulith không coi đó là module và `verify()` sẽ báo sai chỗ khác.

## 2. Domain

- Aggregate root `extends AggregateRoot`, factory `create(...)` / `rehydrate(...)`, không setter.
- Value object = `record` + `Ensure.*` trong compact constructor.
- Event = `record implements DomainEvent`, `type()` = `<module>.<aggregate>.<action>`, chỉ mang giá trị đơn giản.
- Exception = subclass `DomainException(ErrorCategory, "<module>.<reason>", message)`.
- **Zero import Spring/JPA/Jackson.**

## 3. Application

- Mỗi use case: 1 interface `port/in` + record `Command` lồng trong; 1 `@Service @Transactional` package-private ở `service/`.
- Port out hẹp: `LoadXPort`, `SaveXPort`, `XGatewayPort`. Không `Repository<T>` generic.
- Publish event: `events.publishAll(aggregate.pullDomainEvents())` trong transaction.
- `Clock` inject từ bean của `FinanceApplication`.

## 4. Adapter

- `out/persistence`: `XJpaEntity extends AbstractJpaEntity` + `XJpaRepository` + `XPersistenceMapper` (viết tay) + `XPersistenceAdapter implements` port out. Dịch constraint violation → domain exception tại đây.
- `in/web`: controller implement interface sinh từ `api/openapi.yaml` (xem skill `add-api-endpoint`).
- `in/messaging`: xem skill `add-event-consumer`.
- `config`: `@ConfigurationProperties` record + `@Configuration` wiring. Prefix đặt dưới `app.<module>` (mẫu `app.identity.refresh-token-ttl`).

## 5. Migration

`V<n>__<module>_<what>.sql` — xem skill `write-migration`.

## 6. Test (bắt buộc)

| Loại | Nơi |
|---|---|
| Domain | `src/test/…/modules/<name>/domain/model/` — JUnit thuần |
| Use case | `src/test/…/modules/<name>/application/service/` — fake port (mẫu `IdentityFakes`) |
| Luồng thật | `src/integrationTest/` — Testcontainers |

## Checklist

- [ ] `package-info.java` có `@ApplicationModule`
- [ ] `./gradlew test` xanh — gồm 8 luật ArchUnit + `ModularityTest.modulesRespectDeclaredBoundaries`
- [ ] Không có class nào của module khác bị import trực tiếp (chỉ qua `port/in` public hoặc `::events`)
- [ ] Migration Flyway đã thêm, `ddl-auto=validate` pass
- [ ] `./gradlew integrationTest` xanh
- [ ] ADR nếu có quyết định không suy ra được từ `docs/module-template.md`
