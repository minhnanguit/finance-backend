# Rule — Coding standards (Java 21)

## Format & lint

- `./gradlew spotlessApply` trước khi commit. `spotlessCheck` chặn CI.
- google-java-format, `removeUnusedImports`, `formatAnnotations`. Target `src/**/*.java`.
- Compiler args MapStruct đã bật `unmappedTargetPolicy=ERROR` — mapping thiếu là lỗi compile.

## Visibility

Mặc định **package-private**. Chỉ `public` khi thật sự là API vượt ranh giới:

| Loại | Visibility |
|---|---|
| `application/port/in`, `port/out` | `public` (module khác/adapter cần) |
| `application/service/*Service` | package-private, `class XService implements XUseCase` |
| `adapter/in/web/*Controller`, `adapter/in/messaging/*Listener` | package-private |
| `domain/model`, `domain/event`, `domain/exception` | `public` |
| `shared/kernel/*` | `public` |

## Domain model

- Value object = `record`, validate trong compact constructor bằng `Ensure.notBlank / maxLength / lengthBetween / notNull`.
- Aggregate: `extends AggregateRoot`, expose `getX()`, factory tĩnh `create(...)` / `register(...)` và `rehydrate(...)`.
- Không setter, không constructor rỗng ở domain.
- Lỗi nghiệp vụ = subclass `DomainException` với `ErrorCategory` + `code` ổn định dạng `<module>.<reason>` (ví dụ `identity.email_taken`). `shared/web` map category → HTTP status:
  `VALIDATION→400 · NOT_FOUND→404 · CONFLICT→409 · AUTHENTICATION→401 · FORBIDDEN→403 · BUSINESS_RULE→422`.
- **Không bao giờ** ném exception HTTP-specific từ domain/application.

## Application service

```java
@Service
@Transactional
class RegisterUserService implements RegisterUserUseCase {
  // constructor injection, field final, không @Autowired
}
```

- Một use case = một interface trong `port/in` + record `Command` lồng bên trong.
- Port out hẹp theo use case (`LoadUserPort`, `SaveUserPort`, `PasswordHasherPort`) — **cấm** `Repository<T>` generic.
- Thời gian lấy từ bean `java.time.Clock` (inject), không `Instant.now()` rải rác.
- Publish event trong transaction: `events.publishAll(aggregate.pullDomainEvents())`.

## Adapter persistence

- `*JpaEntity extends AbstractJpaEntity`, `*JpaRepository` (Spring Data), `*PersistenceMapper` viết tay, `*PersistenceAdapter implements` port out.
- `save` = load-then-apply khi update, insert khi mới; dịch constraint violation thành domain exception ngay tại adapter.
- `spring.jpa.open-in-view=false`, `ddl-auto=validate` — entity phải khớp migration.

## Logging & null

- SLF4J, không log PII (email, token, password). Client logging đã sanitize header `Authorization`.
- `org.jspecify.annotations.Nullable` là annotation nullability được dùng trong repo.
