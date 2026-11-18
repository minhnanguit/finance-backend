-- Module: ledger — index cho job dọn tombstone (ADR-008 T1).
--
-- Partial index chỉ chứa dòng đã xoá nên rất nhỏ so với bảng. Job hỏi "user nào có tombstone cũ hơn mốc"
-- theo thứ tự user_id (keyset) rồi xoá theo từng user; cả hai đi theo index này.
-- Hệ thống chưa deploy nên dùng CREATE INDEX thường. Khi bảng đã lớn trên production thì index kiểu này phải
-- tạo bằng CREATE INDEX CONCURRENTLY ngoài transaction của Flyway.

CREATE INDEX ix_accounts_tombstones ON accounts (user_id, deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX ix_categories_tombstones ON categories (user_id, deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX ix_transactions_tombstones ON transactions (user_id, deleted_at) WHERE deleted_at IS NOT NULL;
