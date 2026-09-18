---
name: run-local-stack
description: Chạy hạ tầng và app finance-backend ở máy local, và verify một thay đổi trước khi bàn giao. Dùng khi cần "chạy app", "khởi động docker", "chạy test", "kiểm tra build", hoặc khi test lỗi vì thiếu Docker.
---

# Chạy local & verify

## 1. Hạ tầng

```bash
docker compose -f deploy/docker-compose.yml up -d
docker compose -f deploy/docker-compose.yml ps     # chờ healthcheck healthy
```

| Service | Image | Port | Credential |
|---|---|---|---|
| postgres | `postgres:17-alpine` | 5432 | db/user/pass đều `finance` |
| redis | `redis:7-alpine` | 6379 | — |
| rabbitmq | `rabbitmq:4-management-alpine` | 5672, UI 15672 | `finance` / `finance` |

Trên máy dev này: **mở Docker Desktop trước**, nếu không `integrationTest` và `bootRun` sẽ fail.

## 2. Chạy app

```bash
./gradlew bootRun     # http://localhost:8080
```

| Endpoint | Dùng để |
|---|---|
| `GET /actuator/health` | app sống chưa |
| `GET /actuator/modulith` | cấu trúc module runtime |
| `GET /swagger-ui.html` | UI đọc `api/openapi.yaml` (spec tĩnh, không sinh từ code) |
| `GET /openapi.yaml` | hợp đồng thô |
| http://localhost:15672 | RabbitMQ UI — xem queue, message parked |

Biến môi trường: `.env.example` → `DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST/PORT`, `RABBITMQ_*`, `JWT_PRIVATE_KEY_PEM`, `JWT_PUBLIC_KEY_PEM`. Không set JWT keys thì dev dùng cặp RSA ephemeral (token chết sau mỗi lần restart — đúng thiết kế).

## 3. Verify theo phạm vi thay đổi

| Bạn đã sửa | Chạy tối thiểu |
|---|---|
| Chỉ domain / use case | `./gradlew spotlessApply test` |
| Adapter, security, web, messaging | `+ ./gradlew integrationTest` |
| Migration / JPA entity | `+ ./gradlew integrationTest` (Flyway chạy từ đầu trong Testcontainers) |
| `api/openapi.yaml` | `+ ./gradlew openApiGenerate compileJava` rồi `test integrationTest` |
| Trước khi PR | `./gradlew build` (gồm `spotlessCheck` + cả hai suite + bootJar) |

Báo cáo trung thực: chưa chạy được suite nào thì nói rõ là **chưa chạy** kèm lý do, đừng suy đoán kết quả.

## 4. Đọc kết quả

- Report HTML: `build/reports/tests/test/index.html`, `build/reports/tests/integrationTest/index.html`
- Tài liệu module (C4 + module canvas) do `ModularityTest` sinh: `build/spring-modulith-docs`
- `testLogging` chỉ in `failed` và `skipped`, stacktrace FULL — muốn xem log app trong test thì chỉnh tạm `showStandardStreams`, **đừng commit**.

## 5. Dọn

```bash
docker compose -f deploy/docker-compose.yml down        # giữ volume
docker compose -f deploy/docker-compose.yml down -v     # xoá cả dữ liệu — hỏi user trước
```

## Lưu ý

- `./gradlew clean` hiếm khi cần; build cache và parallel đang bật (`gradle.properties`).
- `bin/`, `build/`, `.gradle/` là output, đã gitignore.
- Toolchain khai Java 21, foojay resolver tự tải nếu thiếu. Nếu `JAVA_HOME` của máy trỏ JDK khác mà build lỗi toolchain → **verify before use**, đừng sửa `build.gradle.kts` để né.
