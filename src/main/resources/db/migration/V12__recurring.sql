-- Module: recurring — khoản thu/chi định kỳ: phần cố định + timeline phiên bản (ADR-009).
--
-- Domain là lớp chặn chính và trả mã lỗi; CHECK dưới đây là lớp chặn thứ hai.
--
-- Không FK sang accounts/categories: bảng của module ledger (luật #6). Tham chiếu hợp lệ do domain
-- kiểm qua LedgerReferenceQuery lúc ghi. Không FK sang users (ADR-004 #4).
-- recurring_revisions → recurrings là FK trong cùng module: timeline thuộc aggregate.
--
-- Mọi index của bảng gắn user bắt đầu bằng user_id để sau này partition theo hash(user_id).

CREATE TABLE recurrings
(
  id                    UUID PRIMARY KEY,
  user_id               UUID        NOT NULL,
  name                  VARCHAR(50) NOT NULL,
  type                  VARCHAR(10) NOT NULL,
  currency              VARCHAR(3)  NOT NULL,
  generation_not_before DATE        NOT NULL,
  generation_end_date   DATE,
  remind_days_before    SMALLINT,
  revision              BIGINT      NOT NULL,
  change_seq            BIGINT      NOT NULL,
  version               BIGINT      NOT NULL DEFAULT 0,
  created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
  deleted_at            TIMESTAMP WITH TIME ZONE,
  CONSTRAINT ck_recurrings_name CHECK (length(btrim(name)) BETWEEN 1 AND 50),
  CONSTRAINT ck_recurrings_type CHECK (type IN ('EXPENSE', 'INCOME')),
  CONSTRAINT ck_recurrings_currency CHECK (currency ~ '^[A-Z]{3}$'),
  CONSTRAINT ck_recurrings_not_before CHECK (generation_not_before >= DATE '2000-01-01'),
  CONSTRAINT ck_recurrings_end CHECK (generation_end_date IS NULL
    OR generation_end_date >= DATE '2000-01-01'),
  CONSTRAINT ck_recurrings_remind CHECK (remind_days_before IS NULL
    OR remind_days_before BETWEEN 0 AND 7),
  CONSTRAINT ck_recurrings_revision CHECK (revision >= 1)
);

CREATE UNIQUE INDEX ux_recurrings_user_change_seq ON recurrings (user_id, change_seq);
-- Đếm giới hạn 200 khoản còn hiệu lực (ADR-009 §7).
CREATE INDEX ix_recurrings_user_active ON recurrings (user_id) WHERE deleted_at IS NULL;

CREATE TABLE recurring_revisions
(
  id             UUID PRIMARY KEY,
  recurring_id   UUID         NOT NULL REFERENCES recurrings (id) ON DELETE CASCADE,
  effective_from DATE         NOT NULL,
  anchor_date    DATE         NOT NULL,
  account_id     UUID         NOT NULL,
  category_id    UUID         NOT NULL,
  amount_minor   BIGINT       NOT NULL,
  note           VARCHAR(500),
  frequency      VARCHAR(10)  NOT NULL,
  interval_count SMALLINT     NOT NULL,
  weekday        SMALLINT,
  month          SMALLINT,
  day_of_month   SMALLINT,
  auto_confirm   BOOLEAN      NOT NULL,
  CONSTRAINT ck_recurring_revisions_amount CHECK (amount_minor BETWEEN 1 AND 1000000000000000),
  CONSTRAINT ck_recurring_revisions_dates CHECK (anchor_date >= DATE '2000-01-01'
    AND anchor_date <= effective_from),
  CONSTRAINT ck_recurring_revisions_interval CHECK (interval_count BETWEEN 1 AND 12),
  CONSTRAINT ck_recurring_revisions_rule CHECK (
    (frequency = 'WEEKLY' AND weekday BETWEEN 1 AND 7 AND month IS NULL AND day_of_month IS NULL)
    OR (frequency = 'MONTHLY' AND weekday IS NULL AND month IS NULL
      AND day_of_month BETWEEN 1 AND 31)
    OR (frequency = 'YEARLY' AND weekday IS NULL AND month BETWEEN 1 AND 12
      AND day_of_month BETWEEN 1 AND 31))
);

-- R10: mỗi effective_from tối đa 1 phiên bản; cũng là index đọc timeline theo thứ tự.
CREATE UNIQUE INDEX ux_recurring_revisions_effective
  ON recurring_revisions (recurring_id, effective_from);
