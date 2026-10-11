# ADR-002 – Offline-first sync: cursor theo user, last-write-wins, xoá luôn thắng

**Status:** accepted (design) · 2026-09-12 · **sửa đổi 2026-09-29**, accepted 2026-09-30, cùng plan `docs/LEDGER-PLAN.md` Phase 3–4

> **Thay đổi so với bản 2026-09-12:** cursor là `change_seq` theo từng user thay cho `updated_at` (§2) · 1 cursor cho mọi entity thay cho 1 cursor mỗi scope (§4) · thêm `CONFLICT`/`RETRY` và luật xoá thắng (§3) · thêm SPI `SyncHandler` (§6).

## Context

User ghi chi tiêu lúc không có mạng. DB trên điện thoại là nguồn sự thật trên máy, server phải nhận được các lần ghi trong quá khứ, lộn thứ tự, hoặc gửi 2 lần. Nhiều máy cùng một tài khoản phải hội tụ về cùng một bản.

Bản đầu dùng `updated_at` làm cursor. Cách đó **mất dữ liệu**: `updated_at` lấy lúc transaction bắt đầu, còn dữ liệu chỉ thấy được lúc commit. Transaction T1 (`updated_at = 10:00:00.100`) commit sau T2 (`10:00:00.200`) → client đã pull tới `.200` sẽ không bao giờ thấy T1.

## Decision

### 1. Nguyên tắc (S1, S6)

| # | Quyết định |
|---|---|
| S1 | Mobile **chỉ ghi qua sync**. Không có REST thêm/sửa/xoá riêng cho ledger. Một cửa ghi, một bộ luật kiểm tra |
| — | Mọi entity sync: `id UUID` do client sinh, cột hệ thống `version, created_at, updated_at, deleted_at, change_seq` do server điền |
| — | Sync là **HTTP đồng bộ**. RabbitMQ không nằm trong đường này (ADR-003) |
| S6 | Lần đăng nhập đầu tiên bắt buộc có mạng (Keycloak + seed). Các lần sau offline dùng bình thường |

### 2. Cursor = `change_seq` theo từng user (S2)

```sql
-- trong CÙNG transaction với lần ghi entity
UPDATE user_sync_state SET last_seq = last_seq + 1 WHERE user_id = :me RETURNING last_seq;
```

- Row lock trên `user_sync_state` xếp hàng các lần ghi **của cùng một user** tới lúc commit, nên thứ tự số = thứ tự commit. Pull `change_seq > since` không bao giờ sót.
- User khác nhau không khoá nhau. Nhiều instance backend không phải phối hợp gì.
- **Một dãy số chung cho mọi entity của user** (account, category, transaction, sau này budget...). Client giữ **1 cursor**.
- Cursor gửi cho client là chuỗi opaque (hiện là số thập phân). Client không được tự tính.
- Cursor lớn hơn `last_seq` của server (DB restore) → `409 sync.cursor_ahead`. Client xoá cursor rồi pull lại từ đầu; áp lại là idempotent nên an toàn.

### 3. Conflict (S3, S4)

| Tình huống | Kết quả |
|---|---|
| 2 máy cùng sửa một bản ghi | **Bản tới server sau cùng thắng** (S3). Server không so `version` của client |
| Một máy xoá, máy kia sửa | **Xoá luôn thắng** (S4). UPSERT vào bản ghi đã có `deleted_at` → `CONFLICT` + tombstone |
| Xoá bản ghi đã xoá | `APPLIED`, không tăng `change_seq` |
| Client nhận `CONFLICT` | Ghi đè bản local bằng bản server trả về |

`version` vẫn tăng mỗi lần ghi để debug và để chừa đường cho luật conflict theo entity (ví chung #18).

### 4. Contract

**`POST /api/v1/sync/push`**

| | Chi tiết |
|---|---|
| Body | `deviceId` + `ops[]` tối đa **100**, body tối đa **256 KB** |
| Op | `opId` (UUID), `entity` (`account` \| `category` \| `transaction`), `id`, `action` (`UPSERT` \| `DELETE`), `data` |
| `data` | Chỉ field nghiệp vụ, `additionalProperties: false`. Có `userId`, `changeSeq`, `version`, `createdAt`... → `REJECTED` (ADR-006 B3) |
| Header | `Idempotency-Key` như mọi POST (chống gửi lại nguyên batch trong 24h). `opId` là lớp chống trùng bền 30 ngày |

**Kết quả từng op** (luôn kèm `current`: bản ghi hiện tại trên server, hoặc null)

| Outcome | Khi nào | Client làm gì |
|---|---|---|
| `APPLIED` | Ghi thành công | Xoá khỏi outbox |
| `DUPLICATE` | `opId` này đã xử lý xong trước đó | Xoá khỏi outbox |
| `CONFLICT` | Server giữ bản của mình (đã xoá, S4) | Ghi đè local bằng `current`, xoá khỏi outbox |
| `REJECTED` + `code` | Vi phạm luật domain / giới hạn / không phải của mình | Bỏ op, khôi phục local theo `current` (null → xoá local), báo UI |
| `RETRY` | Ví/danh mục được trỏ tới chưa thấy, lỗi tạm của server, 2 lần sửa đụng nhau | Giữ trong outbox, gửi lại sau backoff 15 giây → tối đa 1 giờ. **Không bao giờ bỏ op**; từ lần thứ 5 UI báo "chưa gửi được" (sửa 2026-10-10, trước đây park sau N lần làm mất thay đổi khi server lỗi vài phút) |

**`GET /api/v1/sync/pull?since=&limit=`**

| | Chi tiết |
|---|---|
| `since` | Cursor lần trước, bỏ trống = từ đầu |
| `limit` | Mặc định 200, tối đa **500** |
| Response | `changes[]` xếp tăng dần theo `change_seq`, `nextCursor`, `hasMore` |
| Change | `entity`, `id`, `changeSeq`, `deleted`, `data` (null khi `deleted = true`) |

- Client lặp pull tới khi `hasMore = false` rồi mới coi là đồng bộ xong.
- Bản ghi client chưa đọc được (entity mới, enum mới từ server mới hơn) **không chặn trang** và cũng **không bị bỏ**: client cất vào `sync_deferred` rồi áp lại khi app được nâng cấp, vì cursor đã đi qua nó (sửa 2026-10-10).
- Một trang được đọc trong **một snapshot** (`REPEATABLE READ`). Mỗi entity là một câu query riêng; ở `READ COMMITTED`, một lần ghi commit giữa 2 câu có thể bị câu đầu bỏ lỡ trong khi câu sau trả số lớn hơn nó, và cursor nhảy qua mất. Trong một snapshot, các số nhìn thấy luôn liền từ 1 vì số được phát dưới row lock.
- Một trang có thể chứa giao dịch trước ví của nó (ví được sửa sau nên `change_seq` lớn hơn). **Bảng ledger trên mobile không đặt FK**, UI join chịu được bản ghi thiếu.

### 5. Lần sync đầu tiên (S5)

- `user_sync_state.initialized_at IS NULL` (đọc dưới row lock của `ChangeSequencer.lock`) → gọi `initialize(user)` của mọi handler **trong cùng request** (ledger: seed danh mục mặc định, ADR-005 §6), rồi đặt `initialized_at`. Không dùng "chưa có dòng" để nhận biết, vì lần ghi nào cũng có thể tạo dòng (upsert trong `ChangeSequencer`).
- 2 request đầu tiên chạy song song vẫn an toàn: id seed là UUIDv5, insert là `ON CONFLICT DO NOTHING`.
- Seed có `change_seq` như mọi lần ghi khác, nên client nhận danh mục qua pull bình thường.

### 6. Kiến trúc: SPI trong `shared.sync`

```
Mobile ─POST /sync/push─►  modules.sync ──SyncHandler──► ledger/adapter/in/sync ──► use case ledger
       ─GET  /sync/pull─►  (điều phối,    (SPI trong     budget, recurring... (sau này)
                            chống trùng,   shared.sync)
                            giới hạn)
```

| Thành phần | Nằm ở | Vai trò |
|---|---|---|
| `SyncHandler` | `shared.sync` (interface) | `entity()` · `apply(UserId, SyncOp) → SyncOpResult` · `current(UserId, id)` · `changesSince(UserId, afterSeq, limit)` · `initialize(UserId)` (mặc định không làm gì). Kèm `SyncData`: đọc chặt `data` của op |
| `ChangeSequencer` | `shared.sync` (interface), impl `JdbcChangeSequencer` ở `modules.sync` (**đã có từ Phase 2**) | `next(UserId)` · `reserve(UserId, count)` cho ghi nhiều dòng · `lock(UserId)` để đọc trước rồi mới lấy đúng số cần. Upsert vào `user_sync_state` nên dòng tự tạo ở lần ghi đầu. Ngoài transaction thì ném lỗi |
| `modules.sync` | module | Controller, điều phối batch, `user_sync_state`, `sync_ops`, job dọn. **Chỉ phụ thuộc `shared`** |
| `ledger` | `adapter/in/sync` | 3 handler (`account`, `category`, `transaction`), mỗi cái gọi `port/in` của ledger |
| `ledger` | `adapter/out/persistence` | Gọi `ChangeSequencer` khi lưu |

- Cùng kiểu dependency inversion với `CurrentUserResolver`: `sync` không biết `ledger` tồn tại, Spring inject `List<SyncHandler>`.
- Thêm module mới = viết thêm handler, **không sửa `sync`** (Open/Closed).
- `shared.sync` được thêm vào luật ArchUnit #7 (chỉ adapter dùng). Vì vậy application của chính module `sync` gọi handler qua port riêng `SyncHandlersPort`; adapter `SyncHandlerRegistry` chuyển tiếp tới các `SyncHandler`.
- `current` (bản ghi trả kèm kết quả op) được `sync` đọc **sau khi** transaction của op kết thúc, không do handler trả về: tránh trả dữ liệu của một lần ghi đã bị rollback.
- Entity lạ → `REJECTED sync.unknown_entity`. Lỗi bất ngờ ở một op → `RETRY sync.server_error`, các op khác vẫn chạy. Hai lần sửa song song đụng optimistic lock → `RETRY ledger.concurrent_update`.
- Handler **trả `SyncOpResult`, không ném lỗi nghiệp vụ ra ngoài**: handler của ledger đổi `DomainException` thành `REJECTED` + `code`, đổi "ví/danh mục chưa thấy" thành `RETRY`. Chỉ module sở hữu mới biết mã lỗi nào nghĩa là gì, nên việc dịch nằm ở handler, không nằm ở `sync`.

### 7. Xử lý một op

**Mỗi op một transaction riêng**, một op lỗi không làm hỏng cả batch:

1. **Transaction của op** (`@Transactional` trên service áp 1 op trong `modules.sync.application.service`, gọi từ service điều phối batch; use case ledger `REQUIRED` join vào):
   0. **Khoá dòng `user_sync_state` của user** (`SyncStatePort.lockWrites`). Op của cùng một user (2 máy push cùng lúc) chạy lần lượt trọn transaction, nên mọi luật "kiểm tra rồi ghi" của handler (≤ 50 ví, ≤ 300 danh mục, ví còn giao dịch thì không xoá, tham chiếu còn sống) thấy đúng dữ liệu đã commit của request kia. Trước đây khoá chỉ được lấy lúc phát `change_seq`, *sau* bước kiểm tra, nên 2 request cùng thấy 49 ví rồi cùng tạo (sửa 2026-10-10, test `SyncApiIT.parallelCreatesNeverExceedTheAccountLimit`, `deleteRacingWithRecordNeverOrphansATransaction`).
   1. `SELECT outcome FROM sync_ops WHERE user_id = :me AND op_id = :opId`. Có và khác `RETRY` → `DUPLICATE`.
   2. Gọi `handler.apply(me, op)`. Handler dùng upsert có `WHERE user_id = :me` (ADR-006 B1).
   3. `APPLIED` → ghi `sync_ops(user_id, op_id, device_id, entity, entity_id, outcome, code, at)` rồi commit.
   4. Khác `APPLIED` → rollback (ném exception nội bộ mang kết quả, service điều phối bắt lại): mọi thay đổi của op bị huỷ, kể cả số `change_seq` đã lấy, nên dãy số không có lỗ. Không dùng `TransactionTemplate` ở đây để giữ luật ArchUnit #3, #8.
2. **Transaction ghi nhật ký** (chỉ khi bước 1 không `APPLIED`): ghi `sync_ops` với outcome `REJECTED` / `CONFLICT` / `RETRY`, `ON CONFLICT (user_id, op_id) DO UPDATE ... WHERE sync_ops.outcome = 'RETRY'`: op `RETRY` được xử lý lại lần sau, còn outcome đã chốt thì không bị đè. `DUPLICATE` không ghi gì.

Tách 2 transaction vì use case ledger ném `DomainException` qua proxy `@Transactional` sẽ đánh dấu cả transaction chung là rollback-only; ghi nhật ký trong đó sẽ bị mất cùng op.

2 request mang cùng `opId` chạy song song: request sau đợi khoá ở bước 1.0 rồi thấy outcome đã chốt ở bước 1.1 → `DUPLICATE`. PK `(user_id, op_id)` ở bước 1.3 vẫn là lớp chặn cuối.

**Thứ tự là việc của client.** Server áp từng op của batch độc lập, theo đúng thứ tự gửi; op trước `RETRY` không chặn op sau. Nên client (mobile `Outbox.sq` `selectEligible`) chỉ đưa vào batch:
- op **đầu tiên** của mỗi bản ghi: 2 op cùng bản ghi chung batch thì bản cũ (đang `RETRY`) có thể đè bản mới về sau;
- op đổi ví/danh mục chỉ khi không còn op trước **trỏ tới** nó: archive/xoá ví đi trước giao dịch của ví thì giao dịch bị `REJECTED ledger.archived` / ví bị `ledger.in_use`;
- client chỉ gộp sửa liên tiếp vào op **cuối** hàng, không gộp vào op giữa hàng (gộp "tạo ví" với "archive ví" ở đầu hàng làm giao dịch ghi giữa hai lần đó bị từ chối rồi bị xoá khỏi máy).

Batch còn bị giới hạn theo dung lượng (≤ 192 KiB ước lượng, dưới mức 256 KiB của server). Server vẫn trả 400/413 cho cả request thì client chia đôi batch tới khi khoanh được op gây lỗi; op đó chờ backoff, các op khác đi tiếp, và pull vẫn chạy.

Pull: mỗi handler trả tối đa `limit` thay đổi có `change_seq > since`; `sync` trộn theo `change_seq`, cắt còn `limit`, `nextCursor` = `change_seq` cuối trang. Đúng vì số là duy nhất trong phạm vi user.

`sync_ops` giữ **30 ngày** (job dọn hằng ngày): vừa chống trùng, vừa là nhật ký (ADR-006 B9).

### 8. Giới hạn tần suất

Push 60/phút, pull 120/phút mỗi user. `RateLimitFilter` trong `shared.web`, fixed window trên Redis (`INCR` + `EXPIRE`). Vượt → `429` + `Retry-After` = số giây còn lại của cửa sổ. Mobile chờ đúng thời gian đó.

## Consequences

**Được**
- Không bao giờ sót thay đổi, kể cả khi nhiều request của cùng user chạy song song.
- Deterministic ở 2 phía, không cần CRDT. Thêm module sync được chỉ bằng một handler.
- Chạy được nhiều instance: sync stateless, lock theo user, rate limit ở Redis chung.

**Chấp nhận**
- Mọi lần ghi của cùng một user xếp hàng qua 1 row lock, giữ suốt transaction của từng op. Một người không ghi nhiều tới mức đó; ghi hàng loạt (import) phải chia batch.
- Máy B archive ví trong lúc máy A (offline) ghi giao dịch mới vào ví đó: giao dịch của A bị `REJECTED ledger.archived` khi A sync. Client không tự tránh được vì 2 thay đổi nằm trên 2 máy. Có nên nới luật `ledger.archived` cho op đến qua sync hay không: **chưa chốt** (xem `docs/LEDGER-PLAN.md`, mục review sau Phase 4).
- Last-write-wins có thể đè một sửa đổi offline cũ lên bản mới hơn. Đủ cho sổ cá nhân.
- Tombstone sống mãi trong plan này. Dọn tombstone (và client có cursor cũ hơn mốc dọn phải pull lại từ đầu) thuộc plan #14.
- Ví chung (#18) sẽ cần luật conflict theo entity. Envelope kết quả từng op đã chừa chỗ.

## References

- ADR-003 (RabbitMQ không trong request path), ADR-005 (ledger), ADR-006 (bảo mật)
- `docs/LEDGER-PLAN.md` mục 2.2, 4
