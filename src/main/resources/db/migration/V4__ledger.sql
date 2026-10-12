-- Module: ledger — ví, danh mục, giao dịch (ADR-005).
--
-- Domain là lớp chặn chính và trả mã lỗi; các CHECK và FK dưới đây là lớp chặn thứ hai, bắt lỗi
-- lập trình hoặc ai đó ghi thẳng vào DB.
--
-- Không FK sang users: không FK chéo module (ADR-004 #4). FK trong module thì dùng khoá ghép
-- (user_id, id) để DB tự chặn bản ghi của user này trỏ vào ví/danh mục của user khác (ADR-006 B1).
--
-- Mọi index bắt đầu bằng user_id để sau này partition theo hash(user_id) không phải sửa query.

CREATE TABLE accounts
(
  id                    UUID PRIMARY KEY,
  user_id               UUID        NOT NULL,
  name                  VARCHAR(50) NOT NULL,
  type                  VARCHAR(20) NOT NULL,
  currency              VARCHAR(3)  NOT NULL,
  opening_balance_minor BIGINT      NOT NULL,
  sort_order            INTEGER     NOT NULL DEFAULT 0,
  archived_at           TIMESTAMP WITH TIME ZONE,
  change_seq            BIGINT      NOT NULL,
  version               BIGINT      NOT NULL DEFAULT 0,
  created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
  deleted_at            TIMESTAMP WITH TIME ZONE,
  CONSTRAINT ux_accounts_user_id UNIQUE (user_id, id),
  CONSTRAINT ck_accounts_name CHECK (name <> ''),
  CONSTRAINT ck_accounts_currency CHECK (currency ~ '^[A-Z]{3}$'),
  CONSTRAINT ck_accounts_opening_balance
    CHECK (opening_balance_minor BETWEEN -1000000000000000 AND 1000000000000000)
);
CREATE UNIQUE INDEX ux_accounts_user_change_seq ON accounts (user_id, change_seq);

CREATE TABLE categories
(
  id           UUID PRIMARY KEY,
  user_id      UUID        NOT NULL,
  kind         VARCHAR(20) NOT NULL,
  name         VARCHAR(50) NOT NULL,
  parent_id    UUID,
  icon         VARCHAR(50),
  color        VARCHAR(7),
  template_key VARCHAR(40),
  archived_at  TIMESTAMP WITH TIME ZONE,
  change_seq   BIGINT      NOT NULL,
  version      BIGINT      NOT NULL DEFAULT 0,
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  deleted_at   TIMESTAMP WITH TIME ZONE,
  CONSTRAINT ux_categories_user_id UNIQUE (user_id, id),
  CONSTRAINT fk_categories_parent FOREIGN KEY (user_id, parent_id) REFERENCES categories (user_id, id),
  CONSTRAINT ck_categories_name CHECK (name <> ''),
  CONSTRAINT ck_categories_not_own_parent CHECK (parent_id <> id),
  CONSTRAINT ck_categories_icon CHECK (icon ~ '^[a-z0-9_]{1,50}$'),
  CONSTRAINT ck_categories_color CHECK (color ~ '^#[0-9A-F]{6}$')
);
CREATE UNIQUE INDEX ux_categories_user_change_seq ON categories (user_id, change_seq);
CREATE INDEX ix_categories_user_parent ON categories (user_id, parent_id) WHERE parent_id IS NOT NULL;

CREATE TABLE transactions
(
  id                 UUID PRIMARY KEY,
  user_id            UUID         NOT NULL,
  type               VARCHAR(20)  NOT NULL,
  status             VARCHAR(20)  NOT NULL,
  account_id         UUID         NOT NULL,
  counter_account_id UUID,
  amount_minor       BIGINT       NOT NULL,
  currency           VARCHAR(3)   NOT NULL,
  category_id        UUID,
  occurred_on        DATE         NOT NULL,
  occurred_at        TIMESTAMP WITH TIME ZONE,
  payee              VARCHAR(100),
  note               VARCHAR(500),
  change_seq         BIGINT       NOT NULL,
  version            BIGINT       NOT NULL DEFAULT 0,
  created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
  deleted_at         TIMESTAMP WITH TIME ZONE,
  CONSTRAINT fk_transactions_account
    FOREIGN KEY (user_id, account_id) REFERENCES accounts (user_id, id),
  CONSTRAINT fk_transactions_counter_account
    FOREIGN KEY (user_id, counter_account_id) REFERENCES accounts (user_id, id),
  CONSTRAINT fk_transactions_category
    FOREIGN KEY (user_id, category_id) REFERENCES categories (user_id, id),
  CONSTRAINT ck_transactions_amount CHECK (amount_minor BETWEEN 1 AND 1000000000000000),
  CONSTRAINT ck_transactions_currency CHECK (currency ~ '^[A-Z]{3}$'),
  CONSTRAINT ck_transactions_occurred_on CHECK (occurred_on >= DATE '2000-01-01'),
  CONSTRAINT ck_transactions_payee CHECK (payee <> ''),
  CONSTRAINT ck_transactions_note CHECK (note <> ''),
  -- Chuyển tiền là 1 dòng (D2): có ví đến khác ví đi, không có danh mục. Thu/chi thì ngược lại.
  CONSTRAINT ck_transactions_shape CHECK (
    (type = 'TRANSFER'
      AND counter_account_id IS NOT NULL
      AND counter_account_id <> account_id
      AND category_id IS NULL)
    OR (type <> 'TRANSFER' AND counter_account_id IS NULL AND category_id IS NOT NULL))
);
CREATE INDEX ix_transactions_user_occurred ON transactions (user_id, occurred_on DESC, id);
CREATE UNIQUE INDEX ux_transactions_user_change_seq ON transactions (user_id, change_seq);
CREATE INDEX ix_transactions_account ON transactions (account_id);
CREATE INDEX ix_transactions_counter_account ON transactions (counter_account_id)
  WHERE counter_account_id IS NOT NULL;
CREATE INDEX ix_transactions_user_category ON transactions (user_id, category_id, occurred_on);
