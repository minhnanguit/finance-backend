-- Module: budget — ngân sách theo danh mục chi, lặp lại mỗi tháng (ADR-007).
--
-- Domain là lớp chặn chính và trả mã lỗi; CHECK và unique index dưới đây là lớp chặn thứ hai.
--
-- Không FK sang categories: bảng của module ledger (luật #6). Danh mục hợp lệ do domain kiểm qua
-- LedgerReferenceQuery; danh mục bị xoá/đổi cha thì consumer ledger.category.changed xoá mềm
-- ngân sách (GS6). Không FK sang users (ADR-004 #4).
--
-- Mọi index bắt đầu bằng user_id để sau này partition theo hash(user_id) không phải sửa query.

CREATE TABLE budgets
(
  id           UUID PRIMARY KEY,
  user_id      UUID       NOT NULL,
  category_id  UUID       NOT NULL,
  currency     VARCHAR(3) NOT NULL,
  amount_minor BIGINT     NOT NULL,
  start_month  DATE       NOT NULL,
  change_seq   BIGINT     NOT NULL,
  version      BIGINT     NOT NULL DEFAULT 0,
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  deleted_at   TIMESTAMP WITH TIME ZONE,
  CONSTRAINT ck_budgets_amount CHECK (amount_minor BETWEEN 1 AND 1000000000000000),
  CONSTRAINT ck_budgets_currency CHECK (currency ~ '^[A-Z]{3}$'),
  CONSTRAINT ck_budgets_start_month
    CHECK (EXTRACT(DAY FROM start_month) = 1 AND start_month >= DATE '2000-01-01')
);

-- G3: mỗi cặp (danh mục, tiền tệ) tối đa 1 ngân sách chưa xoá; chặn luôn 2 request đua nhau.
CREATE UNIQUE INDEX ux_budgets_user_category_currency ON budgets (user_id, category_id, currency)
  WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_budgets_user_change_seq ON budgets (user_id, change_seq);
-- Consumer GS6 tìm mọi ngân sách của một danh mục (kể cả đã xoá, để đếm/no-op cũng dùng index).
CREATE INDEX ix_budgets_user_category ON budgets (user_id, category_id);
