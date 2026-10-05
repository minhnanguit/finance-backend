# ADR-005 – Mô hình dữ liệu ledger: ví, danh mục, giao dịch

**Status:** accepted · 2026-09-30 · triển khai theo `docs/LEDGER-PLAN.md` (root repo) Phase 1–2

## Context

Ledger là nền của các feature MVP #1, #2, #3, #13 và sau này #4, #6, #7, #9. Dữ liệu được ghi offline trên điện thoại rồi sync lên (ADR-002), nên mô hình phải: không sai một đồng, không lệch khi sync thiếu hoặc trùng, và không phải migrate khi thêm Review Inbox, OCR, ngân sách.

## Decision

### 1. Dữ liệu tiền (D1–D9)

| # | Quyết định | Vì sao |
|---|---|---|
| D1 | `amount_minor BIGINT` + `currency CHAR(3)` (ADR-001). Số tiền giao dịch **luôn dương**, chiều tiền do `type` quyết định | Không sai số làm tròn, không nhầm dấu |
| D2 | **Chuyển tiền = 1 dòng** `TRANSFER` với `account_id` (ví đi) → `counter_account_id` (ví đến) | Sync không bao giờ mất một nửa. Không lẫn vào thu/chi |
| D3 | **Phí chuyển khoản** = client tạo thêm 1 giao dịch `EXPENSE` riêng trên ví đi, danh mục `template_key = fee`. Server không tự sinh | Giữ 1 cửa ghi (S1). Báo cáo chi tiêu vẫn thấy phí |
| D4 | **Số dư luôn tính ra**, không lưu, không sync | Không bao giờ lệch với lịch sử |
| D5 | `occurred_on DATE` = ngày theo lịch của user, `occurred_at TIMESTAMPTZ NULL` = thời điểm (null khi không rõ giờ) | Chi lúc 23h không nhảy sang hôm sau |
| D6 | `status` = `CONFIRMED` \| `DRAFT` từ đầu | Review Inbox, OCR chỉ cần tạo nháp |
| D7 | Ví, danh mục đã có giao dịch chỉ **archive**, không xoá | Báo cáo cũ vẫn đúng |
| D8 | Ví đã có giao dịch thì **khoá tiền tệ** | Đổi VND → USD làm số dư vô nghĩa |
| D9 | Danh mục mặc định **copy riêng cho từng user**, id = UUIDv5 cố định | User sửa thoải mái, seed lại không trùng |

### 2. Bảng

```
accounts      id, user_id, name, type[CASH|BANK|EWALLET|OTHER], currency,
              opening_balance_minor, archived_at, sort_order
categories    id, user_id, kind[INCOME|EXPENSE], name, parent_id, icon, color,
              template_key, archived_at
transactions  id, user_id, type[INCOME|EXPENSE|TRANSFER], status[CONFIRMED|DRAFT],
              account_id, counter_account_id, amount_minor, currency, category_id,
              occurred_on, occurred_at, payee, note

Cột hệ thống (mọi bảng, chỉ server điền, ADR-006 B3):
              version, created_at, updated_at, deleted_at, change_seq
```

| Cột | Luật |
|---|---|
| `id` | UUID do **client** sinh (ADR-002). Riêng danh mục mặc định do server sinh bằng UUIDv5 |
| `user_id` | Giá trị là `users.id` nội bộ (ADR-004 #4). **Không có FK constraint** sang `users`: không FK chéo module |
| `archived_at` | Client gửi `archived: true/false`, server điền thời điểm. Client không gửi timestamp |
| `deleted_at` | Chỉ đặt qua op `DELETE` (tombstone). Không bao giờ hard delete trong plan này |
| `change_seq` | Số thứ tự sync theo từng user (ADR-002 §2) |
| `opening_balance_minor` | **Có dấu**, `|x| ≤ 10¹⁵`. Là số dư, không phải số tiền giao dịch nên D1 không áp |
| `counter_account_id` | Bắt buộc khi `TRANSFER`, bắt buộc null khi khác |
| `category_id` | Bắt buộc khi `INCOME`/`EXPENSE`, bắt buộc null khi `TRANSFER` |
| `currency` (giao dịch) | Luôn bằng tiền tệ của ví. Client vẫn gửi để server đối chiếu |
| `categories.kind` | **Bất biến** sau khi tạo |
| `categories.parent_id` | Tối đa 2 cấp: cha phải là gốc (`parent_id IS NULL`), cùng `kind`, cùng user. Danh mục đang có con thì không thành con |
| `categories.icon`, `color` | Không bắt buộc. Mobile tự chọn icon/màu mặc định khi trống |

### 3. Luật domain

Đặt trong aggregate và domain service, không đặt ở controller hay adapter. Mỗi vi phạm ném `DomainException` với mã ổn định. Không dùng `IllegalArgumentException` của `Ensure` cho input từ client, vì sync cần mã lỗi để trả `REJECTED`.

| Luật | Mã lỗi | `ErrorCategory` |
|---|---|---|
| Loại danh mục khớp loại giao dịch (`INCOME` ↔ `INCOME`) | `ledger.category_kind_mismatch` | VALIDATION |
| Tiền tệ giao dịch = tiền tệ ví | `ledger.currency_mismatch` | VALIDATION |
| Chuyển tiền: 2 ví khác nhau, cùng tiền tệ | `ledger.invalid_transfer` | VALIDATION |
| Không **gắn mới** giao dịch hoặc danh mục con vào ví/danh mục đã archive. Giao dịch cũ vẫn sửa được (vd sửa ghi chú) | `ledger.archived` | BUSINESS_RULE |
| Không đổi tiền tệ ví đã có giao dịch (D8) | `ledger.currency_locked` | BUSINESS_RULE |
| Không xoá ví/danh mục đã có giao dịch (D7) | `ledger.in_use` | BUSINESS_RULE |
| Không đổi `kind` của danh mục | `ledger.kind_immutable` | VALIDATION |
| Danh mục quá 2 cấp hoặc cha khác `kind` | `ledger.invalid_parent` | VALIDATION |
| Field sai định dạng hoặc ngoài giới hạn (bảng dưới). Kèm tên field, **không** kèm giá trị (B8) | `ledger.invalid_field` | VALIDATION |
| Vượt số ví/danh mục tối đa mỗi user | `ledger.limit_exceeded` | BUSINESS_RULE |
| Ví/danh mục không phải của user, hoặc đã xoá (ADR-006 B1, B2) | `ledger.not_found` | NOT_FOUND |
| Ví/danh mục **được trỏ tới** chưa thấy: chưa sync tới, hoặc của user khác. Sync đổi thành `RETRY` | `ledger.reference_pending` | BUSINESS_RULE |
| Sửa, archive hay tạo lại bản ghi đã xoá (xoá luôn thắng, ADR-002 S4). Sync đổi thành `CONFLICT` | `ledger.deleted` | CONFLICT |

"Đã có giao dịch" = có ít nhất 1 giao dịch **chưa xoá** trỏ vào (kể cả `DRAFT`, kể cả qua `counter_account_id`). Với danh mục, còn danh mục con chưa xoá cũng tính là đang dùng.

Tạo lại cùng id (client gửi lại) là no-op, trả bản đang có. Id đã thuộc user khác thì `ledger.not_found`, không ghi đè.

### 4. Giới hạn (ADR-006 B4)

| Mục | Giới hạn |
|---|---|
| Số tiền giao dịch | 1 → 10¹⁵ đơn vị nhỏ nhất |
| Số dư ban đầu | −10¹⁵ → 10¹⁵ |
| `occurred_on` | 2000-01-01 → hôm nay (UTC) + 1 năm |
| Độ dài chữ | Tên ví/danh mục 50 · `payee` 100 · `note` 500 ký tự. Cắt khoảng trắng 2 đầu, tên không được rỗng |
| `icon`, `color` | `icon` ≤ 50 ký tự `[a-z0-9_]`. `color` dạng `#RRGGBB` |
| Mỗi user | ≤ 50 ví · ≤ 300 danh mục, tính bản ghi **chưa xoá** (kể cả đã archive) |

Giới hạn nằm ở 2 lớp: domain (nguồn sự thật, trả mã lỗi) và `CHECK` trong DB (lớp chặn thứ 2, V4).

### 5. Số dư (D4)

```
balance(ví) = opening_balance_minor
            + Σ INCOME  (account_id = ví)
            − Σ EXPENSE (account_id = ví)
            − Σ TRANSFER(account_id = ví)
            + Σ TRANSFER(counter_account_id = ví)
chỉ tính giao dịch: deleted_at IS NULL AND status = CONFIRMED
```

- `DRAFT` không vào số dư. Chuyển `DRAFT` → `CONFIRMED` là lúc số dư đổi.
- Số dư được phép âm. App ghi lại thực tế, không chặn chi quá.
- Tổng nhiều ví khác tiền tệ không cộng thẳng (ADR-001): trả tổng theo từng tiền tệ, **bỏ qua ví đã archive** (ví archive đã bị ẩn khỏi danh sách).
- Cùng một công thức ở server (`GetBalances`, SQL SUM) và mobile (SQLite SUM). Test ở 2 phía dùng chung bộ ca.

### 6. Danh mục mặc định (D9)

- `id = UUIDv5(namespace = 369f5fab-8070-4d9d-86ba-0eeda1359ec9, name = "<userId>:<template_key>")`. **Namespace không bao giờ đổi.** Đổi là mọi user bị seed trùng.
- Seed bằng `INSERT ... ON CONFLICT (id) DO NOTHING`: chạy bao nhiêu lần cũng vậy, user đã sửa/archive thì giữ nguyên.
- Được gọi từ lần sync đầu tiên (ADR-002 §5), không qua event.
- `template_key` bắt buộc có `fee` (Phí giao dịch, D3). Danh sách v1:

| `kind` | `template_key` |
|---|---|
| EXPENSE | `food` · `transport` · `shopping` · `bills` · `housing` · `health` · `education` · `entertainment` · `family` · `fee` · `other_expense` |
| INCOME | `salary` · `bonus` · `investment` · `gift` · `other_income` |

Tên hiển thị tiếng Việt lưu thẳng vào `name` lúc seed. Thêm mẫu mới sau này chỉ cần thêm key, user cũ nhận ở lần seed kế tiếp.

### 7. Index

| Index | Phục vụ |
|---|---|
| `transactions (user_id, occurred_on DESC, id)` | Lịch sử, phân trang cursor |
| `(user_id, change_seq)` trên cả 3 bảng, UNIQUE | Sync pull |
| `transactions (account_id)`, `transactions (counter_account_id)` | Số dư, kiểm "đã có giao dịch" |
| `transactions (user_id, category_id, occurred_on)` | Báo cáo, ngân sách sau này |

Mọi index bắt đầu bằng `user_id` (trừ 2 index theo ví, vốn đã thuộc một user), để partition theo `hash(user_id)` sau này không phải sửa query.

**FK trong module dùng khoá ghép** `(user_id, account_id) → accounts (user_id, id)` (tương tự cho ví đến, danh mục, danh mục cha), dựa trên `UNIQUE (user_id, id)` của bảng được trỏ tới. DB tự chặn giao dịch của user này trỏ vào ví của user khác: lớp chặn thứ hai cho ADR-006 B1. Vẫn không có FK sang `users`.

`currency` là `VARCHAR(3)` + `CHECK (currency ~ '^[A-Z]{3}$')` thay vì `CHAR(3)` của ADR-001: cùng bảo đảm, và khớp kiểu `String` khi Hibernate `ddl-auto=validate`.

### 8. Event

`ledger.transaction.recorded` / `.updated` / `.deleted`, payload chỉ có `transactionId`, `userId`, `accountId`, `occurredOn`. **Không** có số tiền, ghi chú, người nhận (ADR-006 B8). Consumer cần chi tiết thì đọc qua `port/in` của ledger.

## Consequences

**Được**
- Tổng thu/chi/số dư chính xác tuyệt đối, cùng công thức ở 2 phía.
- Chuyển tiền không thể lệch một nửa. Review Inbox, OCR, ngân sách thêm vào không phải migrate.
- Luật nằm trong domain nên test bằng JUnit thuần.

**Chấp nhận**
- Phí chuyển khoản là 2 op độc lập. Hiếm khi op phí bị `REJECTED` trong lúc op chuyển được `APPLIED`; client báo lỗi để user ghi lại, không có liên kết cứng giữa 2 dòng trong v1.
- Giao dịch offline ghi vào ví mà máy khác vừa archive sẽ bị `REJECTED ledger.archived`. Mobile báo cho user, không tự nuốt.
- Chuyển tiền khác tiền tệ chưa hỗ trợ. Khi cần: thêm `counter_amount_minor` + `counter_currency`, không đổi mô hình 1 dòng.
- Không có FK sang `users` nên xoá user phải xoá dữ liệu ledger bằng event (plan #14).

## References

- ADR-001 (tiền), ADR-002 (sync), ADR-004 (user id), ADR-006 (bảo mật)
- `docs/LEDGER-PLAN.md` mục 2.1, 3
