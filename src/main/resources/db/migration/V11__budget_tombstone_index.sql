-- Module: budget — index cho job dọn tombstone (ADR-008 T1).
--
-- Cùng vai trò với ix_*_tombstones của ledger (V10): job hỏi user nào có tombstone cũ hơn mốc
-- theo thứ tự user_id rồi xoá theo từng user. Partial index chỉ chứa dòng đã xoá.
-- Hệ thống chưa deploy nên dùng CREATE INDEX thường.

CREATE INDEX ix_budgets_tombstones ON budgets (user_id, deleted_at) WHERE deleted_at IS NOT NULL;
