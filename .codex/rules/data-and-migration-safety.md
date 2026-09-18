# Rule — Dữ liệu & migration

## Flyway là chủ schema

- Vị trí: `src/main/resources/db/migration`, `spring.flyway.enabled=true`.
- Đặt tên: `V<n>__<module>_<what>.sql` (đang có `V1__platform_outbox_and_dedup.sql`, `V2__identity.sql`).
- `spring.jpa.hibernate.ddl-auto=validate` → entity lệch migration thì app **không khởi động được**. Đây là tính năng.
- Schema của outbox Modulith (`event_publication`, `event_publication_archive`, `processed_events`) do Flyway V1 sở hữu; `spring.modulith.events.jdbc.schema-initialization.enabled=false`. Đừng bật lại.

## Bất biến

1. **Không sửa file migration đã merge/chạy.** Sai thì viết file `V<n+1>` để sửa tiếp.
2. Một thay đổi = một file. Không gộp nhiều module vào một migration.
3. Mọi bảng user-data có: `id UUID PRIMARY KEY`, `version BIGINT NOT NULL DEFAULT 0`, `created_at`, `updated_at`, `deleted_at` (đều `TIMESTAMP WITH TIME ZONE`). Bảng gắn người dùng thêm `user_id UUID`.
4. Soft delete: unique index phải có `WHERE deleted_at IS NULL` (xem `ux_users_email`).
5. Hibernate chạy UTC (`hibernate.jdbc.time_zone=UTC`). Đừng lưu local time.
6. Tiền: số nguyên minor units + mã ISO-4217 (ADR-001). **Không** `FLOAT`/`DOUBLE`/`REAL` cho tiền.

## Thay đổi nguy hiểm — làm nhiều bước

Drop/rename cột hoặc bảng đang dùng là **breaking** với instance đang chạy (deploy stateless scale ngang, Flyway migrate tự động):

1. Release N: thêm cột mới, ghi cả hai, đọc cột cũ.
2. Release N+1: backfill, chuyển đọc sang cột mới.
3. Release N+2: drop cột cũ.

Index trên bảng lớn: cân nhắc `CREATE INDEX CONCURRENTLY` (ngoài transaction Flyway) — **verify before use**, repo chưa có tiền lệ.

## Redis

Redis giữ idempotency record (TTL 24h theo `app.idempotency.ttl`), theo thiết kế còn dùng cho rate limit / blacklist. **Redis là cache/ephemeral — không được là nguồn sự thật của dữ liệu nghiệp vụ.**
