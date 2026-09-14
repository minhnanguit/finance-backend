# Adding a module

Copy the shape of `modules/identity` (producer, HTTP in) and `modules/notification` (consumer, messaging in). Every step below is enforced by `ArchitectureRulesTest` / `ModularityTest` unless marked *review*.

## 1. Package skeleton

```
modules/<name>/
├─ package-info.java                 @ApplicationModule(displayName = "...", allowedDependencies = {})
├─ domain/
│  ├─ model/                         aggregate roots (extend AggregateRoot), entities, value objects (records)
│  ├─ event/                         records implementing DomainEvent; package-info with @NamedInterface("events")
│  ├─ service/                       domain services – pure logic across aggregates (optional)
│  └─ exception/                     subclasses of DomainException with a stable code "<module>.<reason>"
├─ application/
│  ├─ port/in/                       one interface per use case + nested Command / Result records
│  ├─ port/out/                      narrow interfaces: LoadXPort, SaveXPort, XGatewayPort ...
│  └─ service/                       @Service @Transactional implementations of port/in
└─ adapter/
   ├─ in/web/                        @RestController implements the generated *Api; a hand-written mapper
   ├─ in/messaging/                  @RabbitListener + queue Declarables via ConsumerQueues.forEvent(...)
   ├─ in/scheduler/                  @Scheduled jobs (optional)
   ├─ out/persistence/               *JpaEntity extends AbstractJpaEntity, Spring Data repo, mapper, adapter implements port/out
   ├─ out/messaging/                 publishers to finance.commands (optional)
   ├─ out/client/                    HTTP clients (optional)
   └─ config/                        @ConfigurationProperties records + @Configuration wiring settings into application
```

`allowedDependencies = {}` means: only `shared`. To use another module's events add `"<module>::events"`.

## 2. Rules you must keep (the 8 from ARCHITECTURE.md §4.3)

| # | Rule | Enforced by |
|---|---|---|
| 1 | `domain` and `shared.kernel` import nothing from Spring / JPA / Jackson / Jakarta | ArchUnit |
| 2 | JPA entities only in `adapter.out.persistence` (+ `shared.persistence`); domain models are mapped, never annotated | ArchUnit |
| 3 | `application` depends only on `domain`, its own ports, `shared.kernel`, `java.*`, `@Service`, `@Transactional` | ArchUnit |
| 4 | Dependencies point inwards; `adapter.in` never touches `adapter.out` and vice versa | ArchUnit |
| 5 | Ports are interfaces (one per use case) or records; no generic `Repository<T>` | ArchUnit + *review* |
| 6 | Modules talk only through `port/in` or `domain.event`; never each other's tables | Spring Modulith `verify()` |
| 7 | `shared.web/persistence/security/messaging` are used by adapters only | ArchUnit |
| 8 | `@Transactional` only in `application.service` | ArchUnit |

> ArchUnit analyses **bytecode**, so an unused `import` is invisible to it (the compiler erases it). Only a real
> dependency - a field, parameter, annotation, call - is a violation. Konsist on the mobile side reads source, so it
> does flag unused imports. Both were verified by deliberately introducing a violation and watching the build fail.

## 3. Conventions

- **Value objects are records** that validate in the compact constructor (`Ensure.*`). Aggregates expose `getX()` accessors and static factories `create(...)` / `rehydrate(...)`.
- **Domain events carry simple values** (UUID, String, Instant) – they are integration contracts. `type()` = `<module>.<aggregate>.<action>`; bump `schemaVersion()` on incompatible change.
- **Publish events from the use case**: `events.publishAll(aggregate.pullDomainEvents())` inside the transaction. Outbox + RabbitMQ happen automatically (`shared.messaging`).
- **Consume events** with a local payload record (never import the producer's class), decode via `InboundEvents`, wrap the handler in `EventDeduplicator.executeOnce(eventId, queueName, ...)`.
- **Errors**: throw a `DomainException` subclass; `shared.web` maps `ErrorCategory` → HTTP status and puts `code` in the problem body. Never throw HTTP-specific exceptions from domain/application.
- **Mappers are hand-written** (`XPersistenceMapper`, `XWebMapper`): explicit, compile-checked, no reflection surprises. MapStruct is acceptable for flat DTO↔DTO cases.
- **Persistence adapter `save`** = load-then-apply for updates, insert otherwise; translate constraint violations into domain exceptions there.
- **Migrations**: one Flyway file per change, `V<n>__<module>_<what>.sql`. Every user-data table has `id UUID, version BIGINT, created_at, updated_at, deleted_at`.
- **Tests**: domain with plain JUnit; application services against in-memory fakes of the ports (see `IdentityFakes`); adapters and the whole flow in `src/integrationTest` with Testcontainers.

## 4. Checklist before opening the PR

- [ ] `./gradlew test` green (architecture rules included)
- [ ] `./gradlew integrationTest` green
- [ ] Contract updated in `api/openapi.yaml` first, controller implements the generated interface
- [ ] New queue declared by the consuming module through `ConsumerQueues.forEvent`
- [ ] Flyway migration added; `ddl-auto=validate` passes
- [ ] ADR written if a decision is not derivable from this template
