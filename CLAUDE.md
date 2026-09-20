# finance-backend — hướng dẫn cho AI agent

## 1. Repo này là gì

Modular monolith (Spring Modulith) phục vụ app Finance mobile.
**Java 21 · Spring Boot 4.1.1 · Spring Modulith 2.1.1 · PostgreSQL · Redis · RabbitMQ · Gradle (Kotlin DSL).**

- Group: `com.mosaicglobal.finance` · version `0.1.0-SNAPSHOT`
- Kiến trúc đã **chốt** ở `../ARCHITECTURE.md` (repo-level, tiếng Việt). Không tự đổi.
- Hợp đồng API `api/openapi.yaml` là nguồn sự thật duy nhất; `finance-mobile` pin bản này.
- Module hiện có: `identity` (reference producer), `notification` (reference consumer).
- Ngoài repo này chỉ còn `finance-mobile` (Kotlin Multiplatform) — **repo riêng, không sửa từ đây**.

## 2. Commands

Mọi lệnh thường dùng đều là target trong `Makefile`. Gõ `make` để xem danh sách đầy đủ.

| Lệnh | Chạy gì |
|---|---|
| `make` | Liệt kê mọi target (mặc định = `make help`) |
| `make up` | Postgres 17 + Redis 7 + RabbitMQ 4 + Keycloak 26 + Mailpit, chờ tới khi healthy — bắt buộc trước `make run` / `make itest` |
| `make run` | App ở `http://localhost:8080` |
| `make health` | Gọi `/actuator/health` xem app sống chưa |
| `make down` | Tắt hạ tầng, giữ dữ liệu |
| `make reset` | Tắt hạ tầng và xoá sạch volume — DB về trắng. **Hỏi user trước** |
| `make test` | Unit + **architecture tests** (ArchUnit + Modulith verify). Không cần Docker |
| `make itest` | Spring context + Testcontainers. **Cần Docker** |
| `make build` | Tất cả ở trên + `spotlessCheck` + boot jar |
| `make fmt` | Format google-java-format (chạy trước khi commit) |
| `make lint` | Chỉ kiểm tra format, không sửa file (giống CI) |
| `make api` | Sinh lại server interface từ `api/openapi.yaml` (tự chạy trước `compileJava`) |
| `make kc` | Mở Keycloak Admin Console — `admin` / `admin` (ADR-004) |
| `make mail` | Mở Mailpit đọc mail verify / reset password |
| `make kc-db` | Tạo database `keycloak` khi volume Postgres đã có sẵn từ trước |

Chạy nối tiếp nhiều việc: `make up run`, `make fmt test itest`.

Không có lint riêng: **Spotless (google-java-format) là lint**; ArchUnit là "lint kiến trúc".
CI (`.github/workflows/ci.yml`) chạy: `spotlessCheck test` → `integrationTest` → `bootJar`. Tag `v*` → release `api/openapi.yaml`.

## 3. Architecture map

```
api/openapi.yaml                      hợp đồng — sửa Ở ĐÂY TRƯỚC, mọi thứ khác theo sau
src/main/java/com/mosaicglobal/finance
├─ FinanceApplication                 @Modulithic entry point + Clock bean
├─ shared/                            nền tảng dùng chung (KHÔNG phải module nghiệp vụ)
│  ├─ kernel/                         Money, UserId, DomainEvent, AggregateRoot, DomainException,
│  │                                  ErrorCategory, Ensure — Java thuần, zero framework
│  ├─ web/                            RFC 7807 problem, cursor paging, idempotency/ (Redis filter)
│  ├─ persistence/                    AbstractJpaEntity (id, version, audit, soft delete)
│  ├─ security/                       resource server stateless, RS256 key config, AuthenticatedUser
│  └─ messaging/                      FinanceExchanges, ConsumerQueues, EventEnvelope,
│                                     EventExternalizationConfig, EventDeduplicator, InboundEvents
└─ modules/<name>/                    domain → application → adapter (xem .claude/rules/architecture-boundaries.md)
src/main/resources/db/migration       Flyway V1 (outbox+dedup), V2 (identity)
src/test/…/architecture               ArchitectureRulesTest (8 luật), ModularityTest
src/integrationTest                   Testcontainers end-to-end
docs/adr/ADR-001..004                 quyết định đã chốt · docs/module-template.md
```

Luồng một event: `@Transactional use case` → `DomainEventPublisher.publishAll` → outbox (`event_publication`, cùng transaction) → sau commit externalize sang exchange `finance.events` → consumer queue → `EventDeduplicator.executeOnce`.

## 4. Rule bắt buộc (luôn áp dụng)

Đọc đầy đủ trong `.claude/rules/`:

| File | Nội dung |
|---|---|
| `architecture-boundaries.md` | 8 luật Clean Architecture + ranh giới module Modulith |
| `contract-first-api.md` | `api/openapi.yaml` trước, controller implement interface sinh ra |
| `coding-standards.md` | Java 21 idiom, record, mapper viết tay, visibility, Spotless |
| `testing-policy.md` | test nào ở đâu, fake port, Testcontainers |
| `data-and-migration-safety.md` | Flyway, `ddl-auto=validate`, cột chuẩn, soft delete |
| `messaging-contracts.md` | routing key, envelope, outbox, dedup, DLX |

Tóm tắt không được vi phạm:
1. `domain` + `shared.kernel` **không** import Spring / JPA / Jackson / Jakarta.
2. JPA entity ≠ domain model; mapper hai chiều **viết tay**.
3. `@Transactional` **chỉ** ở `application/service`.
4. Module nói chuyện qua `port/in` public hoặc domain event — **cấm đụng bảng của nhau**.
5. Mọi POST/PUT/PATCH dưới `/api/` bắt buộc header `Idempotency-Key` (UUID).
6. Migration là file Flyway mới, **không sửa file đã merge**.

## 5. Dùng skill nào khi nào

| Task | Skill |
|---|---|
| Thêm/sửa endpoint HTTP | `.claude/skills/add-api-endpoint/SKILL.md` |
| Tạo module nghiệp vụ mới | `.claude/skills/add-module/SKILL.md` |
| Đổi schema DB | `.claude/skills/write-migration/SKILL.md` |
| Consume domain event từ RabbitMQ | `.claude/skills/add-event-consumer/SKILL.md` |
| Debug 401/403, refresh token, idempotency | `.claude/skills/debug-auth-and-idempotency/SKILL.md` |
| Chạy app/hạ tầng local, verify trước khi bàn giao | `.claude/skills/run-local-stack/SKILL.md` |

## 6. Ghi chú về công cụ

- MCP `code-review-graph`: repo này có cache `.code-review-graph/graph.db`, nhưng `list_repos_tool` trả **0 registered repo** (kiểm tra 2026-09-18). Muốn dùng graph phải chạy `build_or_update_graph_tool` trước; nếu không, fallback Grep/Glob/Read là hợp lệ. **verify before use.**
- `bin/`, `build/`, `.gradle/` là output — đã gitignore, đừng đọc/sửa.
- JDK: toolchain khai báo Java 21 và tự provision qua foojay resolver. Máy dev hiện tại dùng JBR 25 làm `JAVA_HOME` (Gradle vẫn tự lấy 21 cho compile) — nếu build lỗi toolchain, **verify before use**.
