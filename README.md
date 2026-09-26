# finance-backend

Modular monolith for the Finance mobile app. Java 21 · Spring Boot 4.1 · Spring Modulith · PostgreSQL · Redis · RabbitMQ.

The architecture is locked in [`ARCHITECTURE.md`](../ARCHITECTURE.md) (repo-level). This README is the how-to.

## Run locally

Every routine command is a `make` target — run `make` with no argument to list them all.

```bash
make up      # postgres, redis, rabbitmq — waits until all three are healthy
make run     # http://localhost:8080 (Ctrl+C to stop)
```

- Health: `make health` · Modulith: `GET /actuator/modulith`
- API contract & Swagger UI: `make swagger` · `GET /openapi.yaml`
- RabbitMQ UI: `make rabbit` — http://localhost:15672 (finance / finance)
- Stop the infrastructure: `make down` (keeps data) · `make reset` (wipes the volumes)

Try it:

```bash
curl -s -i localhost:8080/api/v1/me     # no token → 401 problem+json
```

Sign-in, sign-up and token refresh happen at Keycloak (`make kc`, ADR-004); this API only validates the token.

Requires a JDK 21 (auto-provisioned by the Gradle toolchain if missing) and Docker for integration tests.

## Build & test

| Command | What runs |
|---|---|
| `make` | List every target in the Makefile. |
| `make test` | Unit tests + **architecture tests** (ArchUnit rules, Modulith verify). No infra needed. |
| `make itest` | Spring context + Testcontainers (Postgres, RabbitMQ, Redis). Needs Docker. |
| `make build` | Everything above + `spotlessCheck` + boot jar. |
| `make fmt` | Format (google-java-format). |
| `make lint` | Check formatting without rewriting files (what CI does). |
| `make api` | Regenerate server interfaces from `api/openapi.yaml` (runs automatically before compile). |
| `make clean` | Delete `build/`. |

Module documentation (C4 diagrams, module canvases) is written to `build/spring-modulith-docs` by `ModularityTest`.

## Layout

```
api/openapi.yaml                 contract – single source of truth, published per release tag
src/main/java/com/uit/finance
├─ FinanceApplication            @Modulithic entry point, Clock bean
├─ shared/
│  ├─ kernel/                    Money, UserId, DomainEvent, AggregateRoot, DomainException – pure Java
│  ├─ web/                       RFC 7807 problems, cursor paging, Idempotency-Key filter (Redis)
│  ├─ persistence/               AbstractJpaEntity (id, version, audit, soft delete)
│  ├─ security/                  stateless JWT resource server, key config, AuthenticatedUser
│  └─ messaging/                 exchanges, consumer queue template, outbox externalization, dedup
└─ modules/
   ├─ identity/                  users, credentials, refresh-token sessions (reference module)
   └─ notification/              consumes identity.user.registered → welcome message (reference consumer)
src/main/resources/db/migration  Flyway (V1 platform tables, V2 identity)
src/test                         unit + architecture tests
src/integrationTest              Testcontainers end-to-end tests
docs/adr                         decisions · docs/module-template.md – how to add a module
```

Each module follows `domain → application → adapter` (see `docs/module-template.md`).

## Contract-first workflow

1. Change `api/openapi.yaml` first. `./gradlew compileJava` regenerates `MeApi` and DTOs; controllers implement the interfaces, so a contract change that is not implemented fails to compile.
2. Tag `vX.Y.Z` → CI attaches the contract to the GitHub release. `finance-mobile` pins that version and generates its client from it.
3. Breaking change → new major, new `/api/v2` path; keep `/v1` for at least two releases.

## Configuration

Environment variables (see `.env.example`): `DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `RABBITMQ_*`, `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWK_SET_URI`, `KEYCLOAK_AUDIENCE`.
