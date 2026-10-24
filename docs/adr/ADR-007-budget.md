# ADR-007 – Ngân sách theo danh mục

**Status:** proposed · 2026-10-17 · chờ review lần 1 · triển khai theo `docs/BUDGET-PLAN.md` (root repo) Phase 1–6

## Context

User muốn đặt mức chi tối đa mỗi tháng cho từng danh mục ("Ăn uống tối đa 3 triệu") và thấy ngay mình đã tiêu bao nhiêu, còn bao nhiêu. Dữ liệu ghi offline rồi sync như ledger (ADR-002), nhiều máy phải thấy như nhau.

Ledger đã chừa sẵn chỗ cho ngân sách: index `transactions (user_id, category_id, occurred_on)` (ADR-005 §7), một dãy `change_seq` chung cho mọi entity (ADR-002 §2), SPI `SyncHandler` để thêm entity mà không sửa `sync` (ADR-002 §6). Nguyên tắc của ADR này: **làm theo khuôn của ledger, không thêm cơ chế mới.**

## Decision

### 1. Nghiệp vụ (G1–G15)

| # | Quyết định | Vì sao |
|---|---|---|
| G1 | **1 ngân sách = 1 danh mục chi cấp 1 + 1 tiền tệ + số tiền mỗi tháng**, kèm `start_month` | Đủ cho sổ cá nhân |
| G2 | Chỉ đặt cho danh mục **cấp 1** (`parent_id IS NULL`, `kind = EXPENSE`). Chi của danh mục con cộng vào cha | Không đếm 2 lần, tổng chỉ cần cộng lại |
| G3 | Mỗi cặp `(danh mục, tiền tệ)` có **tối đa 1** ngân sách chưa xoá | User có ví VND và USD đặt riêng được; không có "bản nào mới đúng" |
| G4 | **Lặp lại mỗi tháng** từ `start_month` (tháng tạo theo lịch của máy). Tháng trước đó không hiện | Không phải tạo lại hằng tháng |
| G5 | Sửa số tiền là sửa cho **mọi tháng**, kể cả tháng cũ | Một dòng một con số, sync đơn giản. Đường giữ lịch sử: §Consequences |
| G6 | **"Đã chi" luôn tính ra, không lưu, không sync** (như ADR-005 D4) | Không bao giờ lệch với giao dịch |
| G7 | Tháng tính theo `occurred_on` (ADR-005 D5) | Không lệch múi giờ |
| G8 | `category_id`, `currency`, `start_month` **bất biến**. Muốn đổi thì xoá rồi tạo mới | Ý nghĩa bản ghi không đổi ngầm |
| G9 | Màu so bằng **số nguyên**: vàng khi `spent ≥ amount − amount/5`, đỏ khi `spent ≥ amount` | Không tràn số, không sai số làm tròn |
| G10 | `%` chỉ để hiển thị và sắp xếp: `spent.toDouble() / amount * 100`, làm tròn xuống, tối đa "999%". Tổng theo tiền tệ cộng **bão hoà** (như số dư, ADR-005 §5) | `spent * 100` có thể tràn `long` |
| G11 | "Mỗi ngày còn được chi" = `còn lại / số ngày còn lại tính cả hôm nay`, làm tròn xuống. Chỉ hiện ở tháng hiện tại khi còn lại > 0 | Tháng cũ không còn ý nghĩa |
| G12 | "Hôm nay" là một luồng tự cập nhật: phát lại lúc 00:00 giờ máy và khi app quay lại foreground | Màn mở qua nửa đêm không kẹt số hôm trước |
| G13 | Danh mục **đã archive**: ngân sách cũ vẫn hiện, gắn nhãn "Đã lưu trữ". Form không cho chọn để tạo mới. **Server không chặn** | Archive chỉ là ẩn (ADR-005 §3, sửa 2026-10-12) |
| G14 | Danh mục **bị xoá** hoặc **thành danh mục con** → server tự xoá mềm ngân sách của nó (§5 GS6) | Ngân sách đó không còn thoả G2 |
| G15 | Tiền tệ mặc định = tiền tệ của ví đang dùng đầu tiên. Chỉ hiện ô chọn tiền tệ khi user có ví nhiều tiền tệ | Đa số user chỉ có VND |

**"Đã chi" của một ngân sách trong một tháng:**

```
spent = Σ amount_minor của transactions
        WHERE type = EXPENSE AND status = CONFIRMED AND deleted_at IS NULL
          AND currency = budget.currency
          AND occurred_on trong tháng đang xem
          AND COALESCE(category.parent_id, category_id) = budget.category_id
```

- Có tính: phí chuyển khoản (danh mục `fee`, ADR-005 D3), giao dịch trong ví đã archive.
- Không tính: `INCOME`, `TRANSFER`, `DRAFT`, khoản đã xoá, khoản khác tiền tệ (thuộc ngân sách của tiền tệ đó nếu có).
- v1 **chỉ mobile tính** (SQLite, cộng tách thương/dư như số dư). Server không có endpoint "đã chi".

### 2. Bảng

```
budgets   id, user_id, category_id, currency, amount_minor, start_month
          + version, created_at, updated_at, deleted_at, change_seq      ← server điền (ADR-006 B3)
```

| Cột / ràng buộc | Luật |
|---|---|
| `id` | UUIDv4 do **client** sinh. Xoá rồi tạo lại thì ra id mới, không vướng "xoá luôn thắng" (ADR-002 S4) |
| `user_id` | `users.id` nội bộ. Không FK |
| `category_id` | **Không FK** sang `categories`: bảng của module khác (luật #6). Hợp lệ do domain kiểm qua `LedgerReferenceQuery` (§6) |
| `currency` | `VARCHAR(3)` + `CHECK (currency ~ '^[A-Z]{3}$')` (như ADR-005 §7) |
| `amount_minor` | `BIGINT`, `CHECK (amount_minor BETWEEN 1 AND 1000000000000000)` |
| `start_month` | `DATE`, `CHECK (EXTRACT(DAY FROM start_month) = 1)` |
| Unique | `(user_id, category_id, currency) WHERE deleted_at IS NULL` — G3, và chặn luôn 2 request đua nhau |
| Index | `(user_id, change_seq)` UNIQUE cho pull · `(user_id, category_id)` cho consumer GS6 |

Migration: `V7__budget.sql` (lấy số trống kế tiếp nếu V7 đã có người dùng lúc merge).

### 3. Luật domain

Mỗi vi phạm ném `DomainException` có mã ổn định; `BudgetSyncHandler` dịch sang outcome (§5).

| Luật | Mã lỗi | `ErrorCategory` |
|---|---|---|
| Danh mục chưa thấy: chưa sync tới **hoặc của user khác** (ADR-006 B2) | `budget.reference_pending` | BUSINESS_RULE |
| Danh mục của mình đã xoá, không phải `EXPENSE`, hoặc không phải cấp 1 | `budget.invalid_category` | VALIDATION |
| Cặp `(danh mục, tiền tệ)` đã có ngân sách chưa xoá (G3) | `budget.duplicate` | CONFLICT |
| Đổi `categoryId` / `currency` / `startMonth` (G8) | `budget.immutable_field` | VALIDATION |
| Quá 100 ngân sách hợp lệ mỗi user (§4) | `budget.limit_reached` | BUSINESS_RULE |
| Số tiền / `startMonth` ngoài giới hạn. Kèm tên field, **không** kèm giá trị (B8) | `budget.invalid_field` | VALIDATION |
| UPSERT id đã thuộc user khác | `budget.not_found` | NOT_FOUND |
| Sửa ngân sách đã xoá (S4) | `budget.deleted` | CONFLICT |

Tạo lại cùng id với cùng dữ liệu là no-op (client gửi lại), như ledger.

### 4. Giới hạn (ADR-006 B4)

| Mục | Giới hạn |
|---|---|
| `amount_minor` | 1 → 10¹⁵ |
| `start_month` | ngày 1, từ 2000-01 tới **tháng UTC hiện tại + 1** (dung sai cho máy ở múi giờ đi trước UTC) |
| Mỗi user | ≤ 100 ngân sách **hợp lệ** (GS8) |

Giới hạn nằm ở domain (nguồn sự thật, trả mã lỗi) và `CHECK` trong DB (lớp chặn thứ hai).

### 5. Sync (GS1–GS9)

| # | Quyết định | Vì sao |
|---|---|---|
| GS1 | Entity mới **`budget`**, chỉ ghi qua `/sync/push` (S1). Không có REST riêng | Một cửa ghi |
| GS2 | Id UUIDv4 do client sinh | Xem §2 |
| GS3 | Last-write-wins + xoá luôn thắng (S3, S4) | Không thêm luật conflict |
| GS4 | 2 máy offline cùng tạo cho một cặp `(danh mục, tiền tệ)` → bản tới sau nhận `REJECTED budget.duplicate` với `current = null`. Client xoá bản local, bản kia về qua pull | Hội tụ về 1 bản, đúng G3 |
| GS5 | Op `budget` trên mobile ghi `outbox_ref` tới danh mục | Op archive/sửa danh mục không chạy trước op ngân sách đang trỏ tới nó (ADR-002 §7) |
| GS6 | Ledger phát **`ledger.category.changed`** (chỉ id) khi danh mục bị xoá hoặc đổi `parentId`. Budget consume, **đọc lại trạng thái hiện tại** của danh mục qua `LedgerReferenceQuery`, rồi xoá mềm mọi ngân sách của nó nếu danh mục đã xoá hoặc không còn là cấp 1. Tombstone tự sync về các máy | Đọc lại thay vì tin event: event trễ, trùng hay sai thứ tự đều không xoá nhầm |
| GS7 | **Mọi lần ghi ngân sách khoá dãy số của user trước** (`BudgetWriteLockPort` → `ChangeSequencer.lock`), kể cả consumer | Consumer không đua với push của cùng user (optimistic conflict → parking lot). Với push, `sync` đã khoá ở ADR-002 §7 bước 1.0; khoá lại trong cùng transaction là no-op |
| GS8 | Đếm giới hạn 100 chỉ tính ngân sách **còn hợp lệ**: khi đã chạm 100, đối chiếu cả lô danh mục qua `LedgerReferenceQuery.findCategories(...)`, xoá mềm bản không hợp lệ, rồi đếm lại | Event chưa chạy hoặc bị park không chiếm chỗ của user |
| GS9 | App cũ chưa biết `budget` cất change vào `sync_deferred` (ADR-002 §4) | Đã có sẵn, không làm thêm |

**Bảng dịch outcome** (trong `BudgetSyncHandler`, theo mẫu `LedgerSyncVerdicts`):

| Trường hợp | Outcome | `code` |
|---|---|---|
| Danh mục chưa tới **hoặc** của user khác | `RETRY` | `budget.reference_pending` |
| Danh mục của mình đã xoá / không `EXPENSE` / không cấp 1 | `REJECTED` | `budget.invalid_category` |
| Cặp `(danh mục, tiền tệ)` đã có (kể cả lỗi unique index lúc đua) | `REJECTED` | `budget.duplicate` |
| Đổi field bất biến | `REJECTED` | `budget.immutable_field` |
| Quá 100 ngân sách hợp lệ | `REJECTED` | `budget.limit_reached` |
| Số tiền / `startMonth` ngoài giới hạn | `REJECTED` | `budget.invalid_field` |
| Field lạ / thiếu / sai kiểu trong `data` | `REJECTED` | `sync.unknown_field` / `sync.invalid_field` (giữ mã của `SyncData`) |
| UPSERT id của user khác | `REJECTED` | `budget.not_found` |
| Sửa ngân sách đã xoá | `CONFLICT` + tombstone | `budget.deleted` |
| 2 lần sửa đụng optimistic lock | `RETRY` | `budget.concurrent_update` |

Contract: thêm `BudgetData` (`additionalProperties: false`; `categoryId`, `currency`, `amountMinor`, `startMonth`), thêm `budget` vào mô tả `entity`. Chỉ thêm, không breaking → **2.2.0**.

### 6. Kiến trúc backend: module `modules/budget`

```
budget ──port/in──► ledger::reference         LedgerReferenceQuery (đọc danh mục)
budget ◄──RabbitMQ── ledger.category.changed   queue budget.category-changed
budget ──SyncHandler──► modules.sync           sync không biết budget tồn tại (ADR-002 §6)
```

| Thành phần | Chốt |
|---|---|
| `package-info` | `@ApplicationModule(displayName = "Budget", allowedDependencies = {"modules.ledger::reference"})`. Tên module Modulith là đường dẫn từ package gốc (`modules.ledger`), không phải `ledger`. **Không** cần `::events`: consumer dùng payload record riêng (messaging rule 2) |
| Domain | Aggregate `Budget` (`create` / `rehydrate`, `changeAmount`, `delete`), value object `BudgetAmount`, `BudgetMonth`. Không import framework (luật #1) |
| `port/in` | `CreateBudgetUseCase` · `UpdateBudgetUseCase` · `DeleteBudgetUseCase` · `ReconcileBudgetsOfCategoryUseCase` · `BudgetChangeFeedUseCase` |
| `port/out` | `LoadBudgetPort` · `SaveBudgetPort` · `BudgetCountPort` · `BudgetWriteLockPort`. Mọi method nhận `UserId`; **thêm `..modules.budget.application.port.out..` vào `USER_SCOPED_OUT_PORTS`** (ArchUnit luật #10) |
| `application/service` | Nơi duy nhất có `@Transactional`. Use case ghi gọi `lock(userId)` đầu tiên (GS7) |
| `adapter/out/persistence` | `BudgetJpaEntity extends AbstractJpaEntity`, mapper viết tay, upsert `ON CONFLICT ... WHERE budgets.user_id = :me`, `change_seq` qua `ChangeSequencer`. Lỗi unique index → `budget.duplicate`. `BudgetWriteLockPort` cài ở đây vì chỉ adapter persistence được dùng `ChangeSequencer` (luật #7) |
| `adapter/in/sync` | `BudgetSyncHandler implements SyncHandler` + `BudgetSyncMapper` (đọc `data` qua `SyncData`) + bảng dịch §5 |
| `adapter/in/messaging` | `CategoryChangedListener` + `CategoryChangedPayload` (record cục bộ) + `BudgetQueues` (`ConsumerQueues.forEvent("budget.category-changed", "ledger.category.changed")`). Bọc trong `EventDeduplicator.executeOnce` |
| Event của budget | **Không phát** — chưa ai cần nghe |

### 7. Thay đổi ở ledger

| Thay đổi | Chi tiết |
|---|---|
| Port tra cứu | Package mới `ledger.application.port.in.reference` có `@NamedInterface("reference")`: `LedgerReferenceQuery.findCategory(UserId, UUID)` và `findCategories(UserId, Set<UUID>)` → `CategoryRef(id, kind, parentId, archived, deleted)`. Không thấy (hoặc của user khác) → rỗng. Budget **không đụng bảng** `categories` |
| Event | `CategoryChanged(eventId, occurredAt, categoryId, userId)`, type `ledger.category.changed`, trong `domain.event` (đã `@NamedInterface("events")`). Phát trong `DeleteCategoryService` và trong `UpdateCategoryService` **chỉ khi `parentId` đổi** (không phát khi đổi tên, màu, icon, archive) |

Payload chỉ mang id (ADR-005 §8, ADR-006 B8).

**Sửa ArchUnit luật #3:** bỏ qua `package-info` giống luật #1. `@NamedInterface` (Modulith) trên `package-info` của `port.in.reference` là metadata module, không phải dependency của use case; không có ngoại lệ này thì không package nào trong `application` expose được cho module khác.

### 8. Mobile

| Module | Chốt |
|---|---|
| `core/common` | `observeToday(clock, timeZone): Flow<LocalDate>` (G12) |
| `core/database` | `Budget.sq` (không FK, như bảng ledger) + `migrations/2.sqm` → schema version 3, cập nhật `UserDatabaseMigrationTest` |
| `core/budget` (mới) | Model, validate khớp §3–4, `BudgetRepository` (entity + outbox + `outbox_ref` trong **một** transaction), `BudgetSyncChangeApplier`. **Module duy nhất đụng bảng `budget`** |
| `core/budget-testing` (mới) | Fake repository, như `core/ledger-testing` |
| `core/ledger` | `SpendingRepository.observeMonthlyExpense(month)`: gom theo `COALESCE(parent_id, category_id)` + `currency`, SUM tách thương/dư như số dư. Dùng index `occurred_on` có sẵn |
| `feature/budgets` (mới) | `ObserveBudgetProgressUseCase` (`combine` ngân sách + chi tiêu + hôm nay → màu, %, mỗi ngày còn được chi) · màn danh sách · form. Một hàm `budgetsNavGraph(...)` |
| `core/designsystem` | `BudgetProgressBar` (`Normal` / `Warning` / `Over`) + token màu **`warning`** light/dark. `Over` dùng `error`. Feature không viết mã màu |
| `core/ui` | Map `budget.*` → câu tiếng Việt |
| `composeApp` | Bỏ placeholder tab `Budgets`, trỏ sang `feature/budgets` |
| Konsist | `BudgetDataRulesTest`: chỉ `core/budget` (và `core/database`) dùng `budgetQueries`. Luật log `amount*` có sẵn phủ luôn `amountMinor` |

### 9. Bảo mật (áp lại ADR-006)

| # | Áp dụng |
|---|---|
| B1, B2 | Port out có `UserId` (luật #10). Upsert có `WHERE user_id = :me`. Danh mục của user khác trả **giống hệt** danh mục chưa tới (`RETRY budget.reference_pending`) |
| B3 | `BudgetData` `additionalProperties: false`; `SyncData` đọc chặt; `user_id`/`version`/`change_seq` do server điền |
| B4 | §4 |
| B5, B6 | Bảng `budget` nằm trong DB SQLCipher riêng của user. Không làm thêm |
| B8 | Log chỉ id, outcome, số lượng. Exception không mang giá trị field. Event chỉ mang id |
| B9 | `sync_ops` tự ghi nhật ký. Không làm thêm |

## Consequences

**Được**
- "Đã chi" không bao giờ lệch với giao dịch vì không lưu. Thêm ngân sách không phải migrate ledger.
- Thêm entity sync chỉ bằng một handler, `sync` không đổi (ADR-002 §6).
- Event trễ, trùng, sai thứ tự hay bị park đều không xoá nhầm và không chiếm chỗ giới hạn của user (GS6, GS8).

**Chấp nhận**
- Sửa số tiền làm đổi cả tháng cũ (G5). Khi cần: thêm `budget_amounts(budget_id, effective_month, amount_minor)`, giữ `budgets.amount_minor` là mức mới nhất cho client cũ.
- Cảnh báo chỉ trong app, không push. Khi cần: budget consume `ledger.transaction.*`, hỏi tổng qua `LedgerReferenceQuery.sumExpense(...)`, `notification` gửi push, bảng `budget_alerts_sent(budget_id, month, threshold)` để mỗi ngưỡng báo 1 lần/tháng.
- Không có ngân sách cho danh mục con, cộng dồn tiền dư, ngân sách tổng tháng, quy đổi tiền tệ.
- Giữa lúc danh mục bị xoá/đổi cha và lúc consumer chạy, ngân sách cũ vẫn tồn tại vài giây; GS8 bảo đảm nó không chặn user tạo mới.
- Xoá tài khoản chưa xoá ngân sách: thuộc plan #14 (budget consume event xoá user như ledger).

## References

- ADR-001 (tiền), ADR-002 (sync), ADR-003 (messaging), ADR-005 (ledger), ADR-006 (bảo mật)
- `docs/BUDGET-PLAN.md` mục 2, 3, 4
