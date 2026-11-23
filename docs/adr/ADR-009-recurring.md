# ADR-009 – Khoản định kỳ

**Status:** accepted · 2026-11-10 · review 2026-11-23 · triển khai `RECURRING-PLAN.md` (root repo), có các điểm đơn giản hoá ghi ở mục "Khác plan"

## Context

User muốn khai báo khoản thu/chi lặp lại (tiền nhà, gói đăng ký, lương), được nhắc trước hạn và có sẵn giao dịch để duyệt khi đến hạn. Dữ liệu ghi offline rồi sync như ledger (ADR-002), nhiều máy phải hội tụ về cùng một kết quả.

Hai bài toán mà khuôn hiện có (LWW, id random) không giải được:

1. **Trùng kỳ.** Máy A và B cùng offline, cùng tới ngày 05/12, cùng tự tạo giao dịch "Tiền nhà". Id random → server có 2 giao dịch. Id cố định nhưng op là `UPSERT` → B lên mạng sau đè mất bản A đã xác nhận và sửa số tiền.
2. **Sửa lịch đua nhau.** A đổi sang ngày 10, B đổi sang ngày 15, cùng offline. LWW → một bản đè bản kia mà không ai biết. Tách thành khoản mới khi sửa → 2 khoản kế nhiệm chạy song song, sinh trùng giao dịch.

Ledger đã có sẵn: id do client sinh (ADR-002 S2), tombstone sống mãi và "xoá luôn thắng" (ADR-002 S4, §155), `SyncHandler.current` trả cả tombstone (ADR-002 §6), `ApplyOpService` áp từng op trong transaction riêng dưới khoá user, trạng thái `DRAFT` không vào số dư (ADR-005 D6).

## Decision

### 1. Nghiệp vụ (R1–R16)

| # | Quyết định | Vì sao |
|---|---|---|
| R1 | **1 khoản định kỳ = phần cố định** (`name`, `type`, `currency`, `generation_not_before`, `generation_end_date?`, `remind_days_before?`) **+ timeline phiên bản** (`revisions`). `type`, `currency` bất biến | Tách cái không đổi khỏi cái đổi theo thời gian |
| R2 | Mỗi phiên bản: `id`, `effective_from`, `anchor_date`, `account_id`, `category_id`, `amount_minor`, `note`, lịch lặp, `auto_confirm`. Phiên bản i áp cho ngày trong `[effective_from_i, effective_from_(i+1))` | Đổi giá tháng này không làm sai tháng trước |
| R3 | Lịch: `WEEKLY` (`weekday` ISO 1–7), `MONTHLY` (`day_of_month` 1–31), `YEARLY` (`month` + `day_of_month`). `interval` 1–12. Không có DAILY, TRANSFER | Đủ cho hoá đơn, đăng ký, lương |
| R4 | **Nhịp tính từ `anchor_date`**: tuần ISO / tháng / năm chứa anchor là kỳ 0, lấy kỳ có khoảng cách chia hết `interval`. `anchor_date ≤ effective_from`. Đổi giá giữ anchor cũ; chỉ đổi anchor khi đổi nhịp | "Mỗi 2 tháng" không lệch nhịp khi chỉ đổi số tiền |
| R5 | Ngày không tồn tại **lùi về cuối tháng** (31 → 30/04; 30 → 28/29 tháng 2; 29/02 → 28/02 năm thường). Luôn tính từ ngày mục tiêu, không lấy ngày đã lùi làm mốc kỳ sau | 31/01 → 28/02 → **31/03** |
| R6 | Ngày theo lịch của máy (`LocalDate`, như ADR-005 D5). Giao dịch sinh ra có `occurred_on` = ngày đến hạn, `occurred_at = null` | v1 không lưu múi giờ riêng cho khoản |
| R7 | Kỳ `d` được sinh khi `max(today − 89, generation_not_before) ≤ d ≤ min(today, generation_end_date)` và có phiên bản hiệu lực | Bù tối đa 90 ngày, không tạo trước hạn |
| R8 | `generation_not_before` **chỉ tăng**. User chọn "chỉ ghi từ hôm nay" (bắt đầu quá khứ, hoặc bỏ qua khoảng bị chặn R13) = nâng mốc này | User quyết định có bù kỳ cũ hay không |
| R9 | **Kỳ trước hôm nay luôn sinh `DRAFT`**. Chỉ kỳ đúng hôm nay, theo phiên bản đã được server chấp nhận, mới theo `auto_confirm` | Không âm thầm ghi hàng loạt vào số dư |
| R10 | Phiên bản đã có hiệu lực **bất biến** (không sửa, không xoá). Phiên bản mới có `effective_from ≥ hôm nay`. Mỗi `effective_from` tối đa 1 phiên bản. Phiên bản còn ở tương lai thì sửa/thay được | Lịch sử bất biến; không có 2 lịch trên cùng một ngày |
| R11 | `generation_end_date` **chỉ rút ngắn**, không gỡ. Đặt trước kỳ đầu = huỷ khoản chưa bắt đầu. Chạy lại = khoản mới | Không mở lại thời gian đã đóng |
| R12 | Danh mục cùng loại với `type`; ví cùng `currency` | Như luật giao dịch (ADR-005 §3) |
| R13 | Ví/danh mục của phiên bản đang hiệu lực **đã archive hoặc đã xoá**: app ngừng sinh, gắn "Cần chú ý". Gỡ chặn thì hỏi user bù (nháp) hay bỏ qua (R8). **Server không chặn** khoản trỏ tới ví/danh mục archive | Archive chỉ là ẩn (ADR-005 §3); không sinh giao dịch ledger sẽ từ chối |
| R14 | Xoá khoản không xoá giao dịch đã sinh. `name` được chụp vào `payee` của giao dịch lúc sinh | Lịch sử tiền là thật |
| R15 | Thông báo nhắc **không chứa số tiền** | Màn hình khoá (tinh thần B8) |
| R16 | Máy giữ lịch cũ có thể sinh kỳ mà timeline mới không còn. Máy đó tự đối chiếu sau khi pull: nháp không khớp → "Cần kiểm tra"; không tự xoá, không tự xác nhận | Không mất dữ liệu, không đảo số dư ngầm |

### 2. Id của kỳ: UUIDv5

```
transaction_id = UUIDv5(NAMESPACE_RECURRING, "<recurringId>/<yyyy-MM-dd>")
NAMESPACE_RECURRING = 9ec1e9e3-0bb0-485a-ba55-77182d53d5f4
```

- `recurringId` dạng chuỗi chuẩn lowercase có gạch nối, ngày ISO-8601, mã hoá UTF-8 (RFC 4122 §4.3, SHA-1).
- **Không** đưa id phiên bản vào tên: hai phiên bản có cùng ngày đến hạn vẫn ra một giao dịch.
- Namespace không bao giờ đổi. Đổi = mọi máy sinh lại toàn bộ kỳ.

**Test vector bắt buộc** (Java ở backend, Kotlin ở mobile, cùng giá trị):

| Tên | UUIDv5 |
|---|---|
| RFC: `NAMESPACE_DNS`, `"www.example.com"` | `2ed6657d-e927-568b-95e1-2665a8aea6a2` |
| `3f1c2b9e-7a4d-4e8b-9c21-5d6e7f8a9b0c/2026-12-05` | `0cdf7899-a9f2-53ca-b1c4-f9475ebfe6a0` |
| `3f1c2b9e-7a4d-4e8b-9c21-5d6e7f8a9b0c/2027-02-28` | `b62a0a6d-780d-538f-9512-a0d2e7fc9cea` |
| `3f1c2b9e-7a4d-4e8b-9c21-5d6e7f8a9b0c/2028-02-29` | `bee84904-e343-5093-9109-a21e2e728584` |

Giao dịch sinh ra là dòng `transactions` bình thường: **bảng và contract ledger không đổi**. Biết một giao dịch thuộc khoản nào bằng cách tính lại id trên timeline (mobile có thêm bảng chỉ-trên-máy cho R16, §8).

### 3. Op `CREATE` trong giao thức sync

`SyncOperation.action` thêm **`CREATE`** (cạnh `UPSERT`, `DELETE`). Chung cho mọi entity, xử lý ở `modules.sync`, handler không phải biết.

Trong `ApplyOpService.apply`, sau `state.lockWrites(owner)` và kiểm `opId` đã chốt:

```
nếu op.action == CREATE:
    nếu handlers.current(owner, entity, id) có (còn sống hoặc tombstone):
        → CONFLICT sync.already_exists   (current = bản đó, như mọi CONFLICT)
    ngược lại:
        → handlers.apply(owner, op với action = UPSERT)
```

| Trường hợp | Outcome |
|---|---|
| Id chưa có với owner | Đường ghi của entity (`APPLIED` hoặc mã lỗi của entity) |
| Id đã có, còn sống | `CONFLICT sync.already_exists` + `current` |
| Id đã có, tombstone | `CONFLICT sync.already_exists` + `current` = tombstone |
| Id thuộc user khác | `current` rỗng → đi `UPSERT` → handler từ chối như `UPSERT` id của người khác (`<entity>.not_found`). Không lộ gì |
| Gửi lại `opId` đã chốt | `DUPLICATE` (có sẵn) |

- **Kiểm rồi ghi an toàn** vì cả hai nằm trong transaction đã khoá user: 2 `CREATE` cùng id của cùng user chạy lần lượt, request sau thấy bản request trước đã commit.
- `CREATE` khi id chưa có với owner **không bao giờ là update**: `current` rỗng nghĩa là owner không có bản nào; handler chỉ còn đường tạo mới hoặc từ chối ownership.
- Chỉ `OpAction` (domain sync) có `CREATE`; `SyncAction` (shared, SPI) **giữ nguyên**: sync đổi `CREATE` sang `UPSERT` sau khi kiểm, handler không bao giờ nhận `CREATE`. Handler cũ không phải sửa.
- Client cũ không gửi `CREATE`. Server cũ (< 2.4.0) nhận `CREATE` sẽ trả 400 vì enum sai: mobile 2.4.0 chỉ chạy với backend ≥ 2.4.0, như mọi lần pin contract.

### 4. Bảng

```
recurrings            id, user_id, name, type, currency,
                      generation_not_before, generation_end_date, remind_days_before,
                      revision
                      + version, created_at, updated_at, deleted_at, change_seq     ← server điền (B3)

recurring_revisions   id, recurring_id, effective_from, anchor_date,
                      account_id, category_id, amount_minor, note,
                      frequency, interval_count, weekday, month, day_of_month, auto_confirm
```

| Cột / ràng buộc | Luật |
|---|---|
| `recurrings.id`, `recurring_revisions.id` | UUIDv4 do **client** sinh. Khoá chính `recurring_revisions` là `(recurring_id, id)` (V13): id phiên bản chỉ cần duy nhất trong một khoản, id trùng giữa hai khoản không làm op kẹt RETRY |
| `revision` | `BIGINT NOT NULL`, số CAS của aggregate (§5 RS8). Khác `version` (optimistic lock của JPA) |
| `type` | `CHECK (type IN ('EXPENSE','INCOME'))` |
| `currency` | `VARCHAR(3)`, `CHECK (currency ~ '^[A-Z]{3}$')` |
| `name` | `VARCHAR(50)`, `CHECK (length(btrim(name)) BETWEEN 1 AND 50)` |
| `remind_days_before` | nullable, `CHECK (BETWEEN 0 AND 7)` |
| `amount_minor` | `BIGINT`, `CHECK (BETWEEN 1 AND 1000000000000000)` |
| `note` | nullable, `VARCHAR(500)` |
| `interval_count` | `CHECK (BETWEEN 1 AND 12)` (tránh từ khoá `interval` của SQL) |
| Tổ hợp lịch | `CHECK` theo `frequency`: WEEKLY ⇔ `weekday NOT NULL AND month IS NULL AND day_of_month IS NULL`; MONTHLY ⇔ chỉ `day_of_month`; YEARLY ⇔ `month` + `day_of_month` |
| `anchor_date ≤ effective_from` | `CHECK` |
| `recurring_revisions.recurring_id` | **FK** → `recurrings(id) ON DELETE CASCADE`: cùng module, được phép |
| `account_id`, `category_id` | **Không FK** sang bảng ledger (luật #6). Hợp lệ do domain kiểm qua `LedgerReferenceQuery` |
| Unique | `(recurring_id, effective_from)` |
| Index | `(user_id, change_seq)` UNIQUE cho pull · `(recurring_id)` |

Migration: `V12__recurring.sql` (V8–V11 đã thuộc vòng đời tài khoản, ADR-008), `V13__recurring_revision_pk.sql`.

### 5. Sync (RS1–RS15)

| # | Quyết định | Vì sao |
|---|---|---|
| RS1 | Entity mới **`recurring`**, chỉ ghi qua `/sync/push`. Timeline đi **trong payload** (`revisions`), không có entity riêng cho phiên bản | Một lần sửa = một op = áp hết hoặc không áp gì |
| RS2 | Id kỳ theo §2 | Mọi máy cùng id cho cùng (khoản, ngày) |
| RS3 | Op `CREATE` theo §3 | Bản tới sau không đè bản đã có |
| RS4 | `current` rỗng không chứng minh id chưa thuộc user khác; ownership do đường ghi của entity kiểm | Không rò dữ liệu |
| RS5 | Bỏ qua một kỳ = xoá nháp → tombstone. Máy khác `CREATE` cùng id → `CONFLICT` tombstone → máy đó xoá bản local | "Xoá luôn thắng" có sẵn |
| RS6 | Outbox mobile không gộp vào `CREATE` (`OutboxWriter.upsert` chỉ gộp khi op cuối là `UPSERT`) | Gộp `CREATE` + `DELETE` rồi bỏ cả 2 thì server không biết kỳ đã bị bỏ qua |
| RS7 | Op `CREATE` giao dịch ghi `outbox_ref` tới ví, danh mục **và recurring** | Không gửi kỳ trước khi khoản vừa tạo lên server |
| RS8 | **CAS:** `RecurringData.revision` trong op là **revision client dựa vào** (tạo mới = 0). Server khác `recurrings.revision` → `CONFLICT recurring.revision_conflict` + `current`; bằng → áp và tăng `revision` đúng 1. Trong pull, `revision` là giá trị hiện tại | Không LWW cho lịch: 2 máy sửa đua nhau đúng 1 thắng |
| RS9 | Mobile: op recurring **không gộp**; mỗi lần ghi local lấy `revision` = revision server đã biết + số op recurring của khoản còn chờ | 2 lần sửa offline liên tiếp lên đúng thứ tự |
| RS10 | `revision_conflict`: mobile bỏ các op **sửa** (UPSERT) còn chờ của khoản, lấy bản server, báo user xem lại. Op `DELETE` đang chờ **được giữ** và gửi tiếp. Không tự gửi lại, không tự trộn | Đơn giản, minh bạch; xoá luôn thắng (ADR-002 S4) |
| RS11 | **Server kiểm timeline** với "hôm nay" = ngày UTC của `Clock` (`T`), dung sai 1 ngày cho múi giờ lệch UTC: phiên bản có `effective_from < T − 1` phải giống hệt bản đã lưu; phiên bản thêm/sửa có `effective_from ∈ [T − 1, T + 366]`. **Tạo mới** được phép 1+ phiên bản với `effective_from ∈ [2000-01-01, T + 366]` | Luật R10 chặn ở server, cho phép tạo khoản bắt đầu trong quá khứ |
| RS12 | `generation_not_before` chỉ tăng, giá trị mới `≤ T + 1`. `generation_end_date` chỉ giảm, không gỡ về null, giá trị mới `≥ T − 1` (có thể nhỏ hơn kỳ đầu = huỷ) | R8, R11 |
| RS13 | Tham chiếu (ví, danh mục) chỉ kiểm cho phiên bản **thêm hoặc sửa**. Phiên bản cũ được phép trỏ tới ví/danh mục đã archive/xoá | Lịch sử không bị chặn vì dữ liệu ledger đổi sau này |
| RS14 | **Mọi lần ghi recurring khoá dãy số của user trước** (`RecurringWriteLockPort` → `ChangeSequencer.lock`); với push đã khoá ở `ApplyOpService`, khoá lại là no-op | Như GS7 |
| RS15 | App cũ chưa biết `recurring` cất change vào `sync_deferred` (ADR-002 §4) | Có sẵn |

Server **không** tự sinh giao dịch: app offline-first, máy biết "hôm nay". Nhờ §2 + §3, thêm server sinh sau này cũng không trùng.

### 6. Luật domain và bảng dịch outcome

Mỗi vi phạm ném `DomainException` có mã ổn định; `RecurringSyncHandler` dịch sang outcome.

| Trường hợp | Outcome | `code` | `ErrorCategory` |
|---|---|---|---|
| `revision` khác server | `CONFLICT` + `current` | `recurring.revision_conflict` | CONFLICT |
| Sửa/xoá phiên bản đã hiệu lực; đổi `type`/`currency`; gỡ hoặc tăng `generation_end_date`; giảm `generation_not_before` | `REJECTED` | `recurring.immutable_field` | VALIDATION |
| Ví hoặc danh mục chưa tới **hoặc** của user khác (B2) | `RETRY` | `recurring.reference_pending` | BUSINESS_RULE |
| Danh mục khác loại `type` | `REJECTED` | `recurring.invalid_category` | VALIDATION |
| Ví khác `currency` | `REJECTED` | `recurring.currency_mismatch` | VALIDATION |
| Ví của phiên bản thêm/sửa đã xoá | `REJECTED` | `recurring.invalid_account` | VALIDATION |
| Field ngoài giới hạn §7, tổ hợp lịch sai, `effective_from` trùng hoặc không tăng dần, ngày ngoài cửa sổ RS11/RS12. Kèm tên field, **không** kèm giá trị (B8) | `REJECTED` | `recurring.invalid_field` | VALIDATION |
| Quá 200 khoản còn hiệu lực | `REJECTED` | `recurring.limit_reached` | BUSINESS_RULE |
| Field lạ / thiếu / sai kiểu trong `data` | `REJECTED` | `sync.unknown_field` / `sync.invalid_field` | — |
| UPSERT id của user khác | `REJECTED` | `recurring.not_found` | NOT_FOUND |
| Sửa khoản đã xoá (S4) | `CONFLICT` + tombstone | `recurring.deleted` | CONFLICT |
| Lỗi đồng thời của DB (không xảy ra khi đã khoá user, giữ cho chắc) | `RETRY` | `recurring.concurrent_update` | — |

Gửi lại đúng dữ liệu đang có với `revision` cũ hơn 1 (client mất response rồi gửi op mới cùng nội dung) vẫn là `revision_conflict`; client lấy `current`, thấy giống thì không báo user (mobile, §8).

### 7. Giới hạn (ADR-006 B4)

| Mục | Giới hạn |
|---|---|
| `name` | trim, 1–50 ký tự |
| `amount_minor` | 1 → 10¹⁵ |
| `note` | ≤ 500 |
| `interval` / `weekday` / `month` / `day_of_month` | 1–12 / 1–7 / 1–12 / 1–31; field không dùng phải null |
| `remind_days_before` | null (tắt) hoặc 0–7 |
| `revisions` | 1–50 mỗi khoản, `effective_from` tăng dần, không trùng |
| Ngày | từ 2000-01-01; cửa sổ ở RS11, RS12 |
| Mỗi user | ≤ 200 khoản **còn hiệu lực** (chưa xoá và `generation_end_date` null hoặc `≥ T − 1`) và ≤ 2.000 khoản **chưa xoá** (kể cả đã kết thúc). Đếm dưới khoá user |

Domain là nguồn sự thật (trả mã lỗi); `CHECK` trong DB là lớp chặn thứ hai.

### 8. Kiến trúc

**Backend**

```
recurring ──port/in──► ledger::reference         LedgerReferenceQuery (ví, danh mục)
recurring ──SyncHandler──► modules.sync          sync không biết recurring tồn tại
sync: CREATE                                     chung, trong ApplyOpService
```

| Thành phần | Chốt |
|---|---|
| `modules.sync` | `CREATE` ở `ApplyOpService` (§3). `OpAction` (domain sync) thêm `CREATE`, `SyncAction` (SPI) giữ nguyên. `SyncWebMapper` đang dùng `OpAction.valueOf(...)`: phải có `CREATE` **cùng lúc** contract 2.4.0 lên server, nếu không op `CREATE` gây lỗi 500 thay vì kết quả từng op |
| Ledger | `LedgerReferenceQuery.findAccount(UserId, UUID)` → `AccountRef(id, currency, archived, deleted)`. Không thấy / của user khác → rỗng |
| `package-info` | `@ApplicationModule(displayName = "Recurring", allowedDependencies = {"modules.ledger::reference"})` |
| Domain | Aggregate `Recurring` (`create` / `rehydrate`, `revise(changes, expectedRevision, today)`, `end(date, today)`, `skipUntil(date, today)`, `delete`), value object `RecurrenceRevision`, `RecurrenceRule`, `RecurringName`. Luật RS11/RS12 nằm trong aggregate, nhận `today` từ service (Clock) |
| `port/in` | `CreateRecurringUseCase` · `UpdateRecurringUseCase` · `DeleteRecurringUseCase` · `RecurringChangeFeedUseCase` |
| `port/out` | `LoadRecurringPort` · `SaveRecurringPort` · `RecurringCountPort` · `RecurringWriteLockPort`. **Thêm `..modules.recurring.application.port.out..` vào `USER_SCOPED_OUT_PORTS`** (luật #10) |
| `adapter/out/persistence` | JDBC (`NamedParameterJdbcTemplate`, như `JdbcOpLog`): ghi khoản + ghi lại toàn bộ timeline (≤ 50 dòng) trong transaction đã khoá user, `change_seq` qua `ChangeSequencer`. Không cần optimistic lock vì mọi lần ghi của user chạy lần lượt (RS14) |
| `adapter/in/sync` | `RecurringSyncHandler` + `RecurringSyncMapper` (`SyncData`) + bảng dịch §6 |
| Consumer / event | **Không có** ở v1. Tham chiếu kiểm lúc ghi (RS13); ví/danh mục bị chặn do app xử lý (R13) |

**Mobile** (chi tiết trong plan)

| Module | Chốt |
|---|---|
| `core/common` | `uuidV5` (SHA-1 Kotlin thuần) + test vector §2 |
| `core/database` | `Recurring.sq` + `migrations/4.sqm` (schema 4 → 5): `recurring`, `recurring_revision`, `recurring_occurrence_local` (chỉ trên máy, không sync: `transaction_id, recurring_id, scheduled_date, revision_id`) |
| `core/recurring` (mới) | Engine lịch thuần Kotlin, repository (CAS RS9), applier, generator, đối soát R16, upcoming. **Nơi duy nhất đụng bảng recurring** |
| `core/ledger` | `recordOccurrenceIfAbsent(database, id, draft)` dùng trong cùng `FinanceDatabase.transaction` với generator: có dòng (kể cả `deleted = 1`) thì bỏ qua, chưa có thì ghi dòng + op `CREATE` |
| `core/sync` | `OutboxAction.CREATE`; op recurring không gộp; xử lý `revision_conflict`; callback sau khi pull xong hết trang |
| Konsist | Chỉ `core/recurring` dùng query recurring; nội dung thông báo không có số tiền |

### 9. Bảo mật (áp lại ADR-006)

| # | Áp dụng |
|---|---|
| B1, B2 | Port out có `UserId` (luật #10). Mọi query có `user_id = :me`. Ví/danh mục của user khác trả giống hệt chưa tới (`RETRY`). `CREATE` id của user khác không lộ gì (§3) |
| B3 | `RecurringData`, `RecurrenceRevisionData` `additionalProperties: false`. `user_id`, `version`, `change_seq` do server điền; `revision` trong op chỉ là điều kiện ghi, server tự tăng |
| B4 | §7 |
| B5, B6 | Bảng recurring nằm trong DB SQLCipher riêng của user trên máy |
| B8 | Log chỉ id, outcome, số lượng. Exception không mang giá trị field. Thông báo không có số tiền (R15) |
| B9 | `sync_ops` tự ghi nhật ký |

## Ví dụ timeline

`T` = ngày hiện tại. Mỗi dòng là `effective_from → nội dung`.

| Tình huống | Trước | Op gửi | Kết quả |
|---|---|---|---|
| **Đổi giá** tiền nhà từ tháng sau, `T` = 10/11 | `rev 0`: `[01/01 → MONTHLY ngày 5, 3tr, anchor 01/01]` | `revision 0`, thêm `[01/12 → ngày 5, 3,2tr, anchor 01/01]` | `APPLIED`, `rev 1`. Kỳ 05/11 vẫn 3tr, 05/12 là 3,2tr; nhịp không đổi |
| **Đổi ngày** 5 → 10, `T` = 10/11, kỳ 05/11 đã ghi | `rev 1` như trên | `revision 1`, thêm `[01/12 → ngày 10, anchor 01/12]` | `APPLIED`, `rev 2`. Kỳ 05/11 giữ nguyên, kỳ kế 10/12. App **mặc định** `effective_from` = đầu kỳ kế tiếp (01/12) vì tháng này đã có kỳ; user chủ động chọn `effective_from` = 10/11 (kèm cảnh báo "tháng này đã ghi ngày 05/11") thì có thêm kỳ 10/11 |
| **Huỷ khoản chưa bắt đầu**, `T` = 10/11 | `rev 0`: `[01/12 → …]` | `revision 0`, `generationEndDate` = 09/11 | `APPLIED`. Không kỳ nào được sinh |
| **2 máy cùng sửa** | `rev 2` | A: `revision 2`, giá 3,5tr · B: `revision 2`, ngày 15 | Op tới trước `APPLIED` (`rev 3`); op sau `CONFLICT recurring.revision_conflict` + `current` (`rev 3`). Máy thua báo user, mở lại form với dữ liệu mới |
| **Sửa phiên bản đã hiệu lực** | `rev 3` | đổi `amountMinor` của `[01/01 → …]` | `REJECTED recurring.immutable_field` |
| **Kéo dài khoản đã kết thúc** | `generationEndDate` = 31/12 | `generationEndDate` = 31/01 hoặc null | `REJECTED recurring.immutable_field` |

## Consequences

**Được**
- Mỗi (khoản, ngày đến hạn) có tối đa một giao dịch sau sync, kể cả nhiều máy offline; kỳ đã bỏ qua không quay lại.
- Op `CREATE` dùng lại được cho entity khác về sau (insert-if-absent), handler không phải sửa.
- Sửa lịch không đổi lịch sử; 2 máy sửa đua nhau không bao giờ ra 2 lịch.
- Contract và bảng ledger không đổi.

**Chấp nhận**
- Máy giữ lịch cũ khi offline có thể sinh một kỳ mà lịch mới không còn (R16). Kỳ đó là nháp, chỉ máy sinh ra nó gắn "Cần kiểm tra"; máy khác thấy như một nháp bình thường có payee = tên khoản.
- Ranh giới "đã hiệu lực" dung sai 1 ngày quanh UTC (RS11). Trong ngày dung sai, máy ở múi giờ lệch có thể sửa được phiên bản vừa bắt đầu hôm qua theo giờ của nó.
- Không tạm dừng, không múi giờ riêng cho khoản, không TRANSFER/DAILY.
- `revision_conflict` bỏ bản sửa local; user phải sửa lại. Khi cần: kho lưu "đề xuất" phía máy.
- Kỳ đã bỏ qua không quay lại nhờ tombstone + `CREATE`, **trong cửa sổ 90 ngày**: tombstone giữ ≥ 92 ngày (`SyncProperties.MIN_TOMBSTONE_RETENTION`, app không khởi động nếu cấu hình ngắn hơn), còn app chỉ sinh kỳ trong 90 ngày gần nhất (R7). Ngoại lệ đã biết: một máy giữ nháp **chưa gửi** quá 92 ngày (offline lâu) rồi mới gửi `CREATE`, sau khi tombstone của lần bỏ qua ở máy khác đã bị dọn → nháp quay lại, user bỏ qua lần nữa. Không ảnh hưởng số dư.
- Xoá tài khoản xoá luôn dữ liệu recurring (`RecurringUserDeletedListener`).
- Server không tự sinh: user không mở app thì không có giao dịch (thông báo cục bộ vẫn nhắc).

## Khác `RECURRING-PLAN.md` (chốt khi review 2026-11-23)

Chọn hướng ít bảng/ít cơ chế hơn, mở rộng được về sau; mỗi điểm ghi lại cam kết bị thu hẹp.

| Plan | ADR này | Lý do / cam kết thay đổi |
|---|---|---|
| Mục 6: bảng dấu xoá `recurring_deleted`, `ledger_recurring_deleted_transaction` giữ tới khi xoá tài khoản | Không có bảng dấu xoá. Tombstone theo retention chung, tối thiểu 92 ngày > cửa sổ 90 ngày | Dữ liệu không phình theo số lần xoá trong cả vòng đời tài khoản; ledger giữ nguyên. Cam kết "không hồi sinh" chỉ còn trong cửa sổ 90 ngày (Consequences) |
| Mục 5.3: cột nguồn `source_recurring_id`, `source_revision_id`, `scheduled_date` trên giao dịch + SPI guard + `recurring.invalid_occurrence` | Ledger và contract ledger không đổi; nguồn gốc tính lại bằng UUIDv5; mobile có `recurring_occurrence_local` | Không đổi ledger hai phía, không lo tương thích app cũ. Server không chặn kỳ sinh từ lịch cũ (plan 5.4): chỉ máy sinh ra kỳ đó gắn "Cần kiểm tra" (R16) |
| Mục 4.2: múi giờ IANA riêng mỗi khoản | Múi giờ máy (R6) | User ở một múi giờ; thêm cột về sau không phá dữ liệu cũ |
| Mục 5.1: giữ bản sửa của user làm "đề xuất" khi `revision_conflict` | Bỏ bản sửa local, giữ op `DELETE` đang chờ (RS10) | Trường hợp hiếm; xoá vẫn luôn thắng (ADR-002 S4) |
| Mục 7.1: `record_from`, `reminder_local_time` | Không có; nhắc lúc 09:00 | Chưa có UI cần tới |
| Mục 7.2: 128 phiên bản/khoản, payload ≤ 64 KiB | 50 phiên bản/khoản (payload tự bị chặn theo đó) | Chặt hơn plan |
| Mục 9: iOS `BGAppRefreshTask` | Chỉ Android chạy nền; iOS sinh kỳ khi mở app | Plan coi chạy nền là best-effort, mở app luôn bù |

## References

- ADR-002 (sync: S2, S4, §4, §6, §7), ADR-005 (ledger: D5, D6, §3), ADR-006 (bảo mật), ADR-007 (khuôn module dữ liệu theo user)
- RFC 4122 §4.3 (UUID name-based, SHA-1)
- `RECURRING-PLAN.md` v2
