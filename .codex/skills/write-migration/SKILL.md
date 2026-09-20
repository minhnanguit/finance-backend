---
name: write-migration
description: Viết Flyway migration cho finance-backend (thêm bảng, thêm cột, index, backfill). Dùng khi task đụng schema PostgreSQL, "tạo bảng", "thêm cột", "đổi kiểu dữ liệu", "thêm index".
---

# Viết Flyway migration

Bắt buộc đọc: `.codex/rules/data-and-migration-safety.md`.

## Trước khi viết

```bash
ls src/main/resources/db/migration     # số version cao nhất hiện tại
```

Đặt tên file mới: `V<n+1>__<module>_<what>.sql` (ví dụ `V3__transactions_table.sql`).
**Không sửa file đã tồn tại.** Nếu file đã merge bị sai, sửa bằng migration tiếp theo.

## Khuôn bảng user-data

```sql
-- Module: <name>
CREATE TABLE <table>
(
  id         UUID PRIMARY KEY,
  user_id    UUID NOT NULL,                      -- nếu dữ liệu thuộc về user
  -- ... cột nghiệp vụ ...
  version    BIGINT NOT NULL DEFAULT 0,          -- optimistic locking (AbstractJpaEntity)
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_<table>_user_occurred ON <table> (user_id, occurred_at);
CREATE UNIQUE INDEX ux_<table>_<cols> ON <table> (<cols>) WHERE deleted_at IS NULL;  -- soft delete aware
```

Quy ước tên index trong repo: `ix_` cho index thường, `ux_` cho unique.

## Kiểu dữ liệu

| Dữ liệu | Kiểu |
|---|---|
| Khoá | `UUID` |
| Thời điểm | `TIMESTAMP WITH TIME ZONE` (app chạy UTC) |
| Tiền | `BIGINT` minor units + `VARCHAR(3)` currency ISO-4217 (ADR-001). **Cấm** FLOAT/DOUBLE/NUMERIC-float |
| Enum | `VARCHAR(20..30)` + check ở domain, không dùng enum type của Postgres |
| Hash/token | Chỉ lưu hash (mẫu `refresh_tokens.token_hash` SHA-256), không lưu giá trị gốc |

## Sau khi viết

1. Cập nhật / tạo `*JpaEntity` khớp **chính xác** cột: `ddl-auto=validate` sẽ chặn app khởi động nếu lệch.
2. Chạy:

```bash
make up
make itest        # Testcontainers chạy Flyway từ đầu → phát hiện migration hỏng
```

3. Muốn xem trên DB local đang chạy: `make run` (Flyway migrate lúc startup).

## Thay đổi phá vỡ

Drop/rename cột đang dùng ⇒ chia 3 release (thêm → backfill + chuyển đọc → drop). Deploy là stateless scale ngang, instance cũ và mới chạy song song trong lúc rollout.

Nếu task yêu cầu drop trực tiếp: nói rõ rủi ro cho user, đề xuất phương án 3 bước, **và chỉ làm khi user xác nhận**.

## Bảng nền tảng — đừng đụng

`event_publication`, `event_publication_archive`, `processed_events` thuộc V1, do Modulith + `EventDeduplicator` dùng. Chỉ đổi khi nâng cấp Spring Modulith và có ADR.
