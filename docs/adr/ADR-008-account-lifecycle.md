# ADR-008 – Vòng đời tài khoản và dữ liệu: xoá tài khoản, khoá app, dọn tombstone

**Status:** accepted · 2026-11-07 · hiện thực theo `docs/SETTINGS-SECURITY-PLAN.md` (repo gốc), mã quyết định A/K/P/T/C giữ nguyên

## Context

- App Store 5.1.1(v), Google Play và Nghị định 13/2023 (Điều 16) bắt buộc: user tự xoá được tài khoản **trong app**,
  và dữ liệu cá nhân bị xoá thật trong thời hạn hợp lý.
- Offline-first (ADR-002) nên dữ liệu của một user nằm ở nhiều nơi: Postgres (ledger, sync), Keycloak (ADR-004),
  file SQLCipher trên từng máy (ADR-006 B5). Máy khác của cùng user có thể đang offline lúc xoá.
- Backend scale ngang, stateless; cache theo instance; RabbitMQ at-least-once qua outbox (ADR-003).
- ADR-002 để tombstone sống mãi và ADR-006 để trống "khoá app, che màn hình" — đều dồn về đây.

## Decision

### Xoá tài khoản (backend)

| # | Quyết định |
|---|---|
| A1 | `DELETE /api/v1/me` → `202` + `AccountDeletionStatus {status, requestedAt}`. Không `Idempotency-Key`: gọi lại trả `403 identity.user_deleted` |
| A2 | Chỉ nhận token có `auth_time` trong 5 phút (+1 phút lệch đồng hồ); không thì `403 identity.reauth_required`. Client đăng nhập lại bằng `prompt=login` + `max_age=0` |
| A4 | **Hai pha**. Request: 1 transaction `ACTIVE → DELETING`, xoá PII, outbox `identity.user.deletion-requested`. Sau `purge-delay` (mặc định 10 phút, job mỗi phút, `FOR UPDATE SKIP LOCKED`): `DELETING → DELETED`, outbox `identity.user.deleted` |
| A5 | Giữ dòng `users` (`DELETED`, `external_subject`, PII đã thay bằng giá trị vô danh). JIT provisioning gặp subject đã xoá → `403`, không tạo lại |
| A6 | Mọi request của user `DELETING`/`DELETED` → `403 identity.user_deleted`, kiểm trong `CurrentUserResolver` (trước mọi controller), cache 30 giây/instance, instance xử lý request xoá cập nhật ngay sau commit |
| A7 | `ledger`, `sync` (và `budget` sau này) tự xoá bảng của mình khi có `identity.user.deleted`. Consumer idempotent, lỗi → retry → parking lot |
| A8 | Tài khoản Keycloak bị xoá bởi **job** `IdentityProviderRemovalJob` (mỗi 15 giây) đọc **hàng đợi bền** `users.idp_removed_at IS NULL` của user `DELETING`/`DELETED`, gọi Admin API **ngoài** transaction DB, xong thì đánh dấu. Keycloak lỗi → dừng lô, lần sau thử lại, không giới hạn; tồn đọng quá 1 giờ → log ERROR. Client tắt bằng cấu hình → không đánh dấu, bật lại thì làm bù. Client confidential `finance-backend`, service account chỉ có `realm-management/manage-users`, `RestClient` thuần, `404` = đã xoá. Secret **không có giá trị mặc định** trong code |

Ràng buộc khởi động: `purge-delay ≥ account-status-cache-ttl`. Lý do: trong tối đa một TTL, instance khác còn tin
user đang `ACTIVE` và nhận push; xoá dữ liệu trước thời điểm đó thì một lần push muộn tạo lại dòng sau khi đã dọn.

Vì sao A8 không dùng consumer RabbitMQ (bản đầu dùng, đổi sau review 2026-11-07): retry của container chỉ ~15 giây
(1+2+4+8) rồi vào `.parked` và không tự phát lại — Keycloak nâng cấp vài phút là tài khoản (email, mật khẩu) nằm lại
mãi; lời gọi HTTP lại nằm trong transaction dedup, giữ connection DB. Cột trên dòng `users` là hàng đợi bền, tự
quan sát được (đếm dòng chờ) và không phụ thuộc broker.

### Tombstone, cursor, event store

| # | Quyết định |
|---|---|
| T1 | `sync` điều phối dọn tombstone quá 90 ngày qua SPI `SyncHandler`; mỗi module xoá bảng của mình. Mỗi owner một transaction, giữ khoá ghi của user (như push). Tombstone còn bị tombstone khác trỏ tới (ví ← giao dịch đã xoá) được dọn bằng cách **lặp tới khi một lượt không xoá thêm gì**: không phụ thuộc thứ tự handler, cả chuỗi xong trong một lần chạy |
| T2 | `user_sync_state.purged_through_seq` = `change_seq` lớn nhất đã dọn (chỉ tăng) |
| T3 | Cursor `<afterSeq>[.<floor>]`. Pull từ đầu nhận `floor = purged_through_seq`. Cursor có `purged_through_seq > max(afterSeq, floor)` → `410 sync.cursor_expired` (`ErrorCategory.GONE`) |
| T5 | Giữ tombstone (90 ngày) ≥ giữ `sync_ops` (30 ngày), kiểm khi khởi động |
| T6 | Xoá `event_publication` đã hoàn tất + `processed_events` quá 30 ngày, theo lô |

### Mobile

| # | Quyết định |
|---|---|
| A9 | `202` hoặc `403 identity.user_deleted` → xoá DB + khoá + token + cài đặt theo user. Tín hiệu từ máy khác chỉ có hiệu lực khi access token bị từ chối **vẫn là token hiện tại**. Máy không kịp nhận 403 (xem Chấp nhận) → màn đăng nhập có "Xoá dữ liệu trên máy này" khi không còn phiên mà máy còn sổ |
| A2' | Lỗi không rõ kết quả lúc gửi xoá (đứt kết nối, 5xx) → app hỏi lại `GET /me` bằng token mới: `403 user_deleted` = đã xoá, `200` = chắc chắn chưa, lỗi tiếp = báo "chưa rõ". Đoạn từ lúc gửi xoá tới lúc dọn máy không bị huỷ khi màn bị đóng. Gắn token mới vào phiên theo **user**, không theo refresh token (sync nền có thể vừa xoay token) |
| A10 | Đổi mật khẩu: authorization request kèm `kc_action=UPDATE_PASSWORD` (Keycloak AIA). `kc_action_status=success` → đổi code, kiểm `sub`, **thay phiên trên máy bằng token mới** (ô "Đăng xuất khỏi thiết bị khác" có thể huỷ phiên cũ của app); `cancelled` → bỏ qua. Không dùng Account Console |
| A11 | Xác nhận lại (A2) và đổi mật khẩu (A10) mở trình duyệt **ephemeral** (Android `setEphemeralBrowsingEnabled`, Chrome 136+; iOS vốn ephemeral). Phiên app không còn là phiên SSO của trình duyệt, nên nút "Restart login" (`skip_logout=false`) hay đăng nhập tài khoản khác trong trình duyệt không đóng được phiên app. Đăng nhập/đăng ký giữ trình duyệt thường (link xác minh email mở ở trình duyệt thường vẫn nối được). Đổi mật khẩu xong, phiên app vừa bị thay được đóng nếu khác `sid` |
| ~~K, P1, P2~~ | **Gỡ 2026-11-12**: khoá app, chặn chụp màn hình, che recents chưa cần. Các dòng K/P1/P2 dưới đây là thiết kế để làm lại khi cần; P3, P4 vẫn hiệu lực |
| K1, K2 | Khoá app dùng xác thực của hệ điều hành (`BiometricPrompt` STRONG\|DEVICE_CREDENTIAL, `LAPolicyDeviceOwnerAuthentication`), là lớp UI, **không** gắn vào khoá DB |
| K6 | Màn khoá là **dialog toàn màn hình** (window/layer tạo sau): nằm trên mọi dialog, bottom sheet, menu đang mở, nuốt nút back, giữ focus. Nội dung bên dưới bị gỡ khỏi cây accessibility khi khoá |
| K4 | Rời app vì chính app (trình duyệt đăng nhập lại, hộp thoại xác thực) được thêm 5 phút; bỏ đi lâu hơn giữa chừng thì quay lại vẫn khoá |
| K5, K7 | Cài đặt khoá theo user, bị xoá khi đăng xuất / xoá tài khoản |
| P1–P4 | Che nội dung khi app không active; Android 13+ tắt ảnh recents; `FLAG_SECURE` mặc định bật; DB không vào backup / device transfer (Android `dataExtractionRules`, iOS `NSURLIsExcludedFromBackupKey` trên thư mục DB) |
| T4 | `410` → push outbox trước, rồi xoá bản sao đã đồng bộ (giữ dòng còn trong outbox) + cursor trong một transaction, pull lại từ đầu |

## Cách làm cụ thể

```
DELETE /me ──tx──► users: DELETING, PII xoá, idp_removed_at = NULL ──outbox──► identity.user.deletion-requested
IdentityProviderRemovalJob (mỗi 15 giây, ngoài transaction) ──► Keycloak DELETE user ──► idp_removed_at = now
UserDeletionFinalizerJob (mỗi 1 phút, quá purge-delay)
           ──tx──► users: DELETED ──outbox──► identity.user.deleted
                                               ├─► ledger.user-deleted  → transactions → categories → accounts
                                               ├─► budget.user-deleted  → budgets
                                               └─► sync.user-deleted    → sync_ops, user_sync_state
```

- Mã lỗi mới: `identity.reauth_required` (403), `identity.user_deleted` (403), `sync.cursor_expired` (410).
- Realm có từ trước (`--import-realm` chỉ import realm mới): `make up` chạy `deploy/keycloak/configure-realm.sh`, tạo client
  `finance-backend` nếu thiếu, đồng bộ secret với `deploy/.env`, gán `manage-users`. Chạy lại bao nhiêu lần cũng được, không
  cần `make reset`.
- Secret của `finance-backend`: local thì `make up` sinh ngẫu nhiên một lần vào `deploy/.env` (Keycloak nhận lúc import
  realm, `make run` truyền cho backend); production lấy từ secret manager. Thiếu secret → log ERROR lúc khởi động, việc
  xoá Keycloak nằm chờ (không mất) tới khi cấu hình đúng.
- `403 identity.user_deleted` do `ProblemAuthenticationEntryPoint` trả (exception `InactiveAccountException` của
  `shared.security`), nên mọi endpoint có token đều nhất quán mà không controller nào phải biết.
- Port out mới của ledger (`PurgeLedgerPort`, `TombstonePort`) và budget (`PurgeBudgetsPort`, `BudgetTombstonePort`) vẫn nhận `UserId` (luật #10). Dọn tombstone liệt kê owner
  bằng keyset trên UUID theo **thứ tự không dấu của Postgres** (`SyncHandlerRegistry.POSTGRES_UUID_ORDER`) — `UUID.compareTo`
  của Java so có dấu, gộp danh sách theo thứ tự đó sẽ bỏ sót owner.
- Contract: endpoint chỉ có response lỗi mang body thì openapi-generator khai `produces = application/problem+json` và
  Spring trả `406` cho client gửi `Accept: application/json` (Ktor mặc định). Vì vậy response thành công luôn có body JSON;
  `ApiClient` của integration test gửi `Accept: application/json` để bắt lỗi này.
- Cấu hình: `app.identity.account-status-cache-ttl` (30s), `app.identity.deletion.{purge-delay,finalize-interval}`
  (10m, 1m), `app.identity.keycloak-admin.*` (env `KEYCLOAK_ADMIN_*`), `app.sync.tombstone-{retention,purge-cron}`,
  `app.messaging.{retention,cleanup-cron}`.

## Consequences

**Được**
- Xoá thật trên server, Keycloak và mọi máy; không ai phải chạy tay. Lỗi giữa chừng → retry / parking lot, không mất bước.
- Không có cửa sổ "đã đánh dấu xoá mà quên phát event" (outbox cùng transaction).
- Bảng tombstone, `sync_ops`, outbox, dedup đều có hạn sống — dung lượng tỉ lệ với dữ liệu sống, không với lịch sử.
- Đổi mật khẩu dùng form của Keycloak (kể cả bước hỏi lại mật khẩu cũ), app không giữ mật khẩu nào.

**Chấp nhận**
- Dữ liệu ledger còn tối đa ~purge-delay + 1 phút sau khi user bấm xoá (đã khoá truy cập, PII đã xoá ngay).
- Mỗi request có token thêm một lần đọc `users` theo khoá chính mỗi 30 giây mỗi user (cache).
- Chưa có khoá app / chặn chụp màn hình (gỡ 2026-11-12): người cầm máy đang mở khoá xem được sổ; xoá tài khoản vẫn
  bắt đăng nhập lại (A2), đổi mật khẩu vẫn do Keycloak hỏi lại mật khẩu cũ.
- Máy offline > 90 ngày phải tải lại sổ (thay đổi chưa gửi vẫn được push trước).
- Dòng `users` của tài khoản đã xoá không bao giờ bị xoá (vô danh) — giữ để chặn token cũ tạo lại user.
- Máy khác chỉ nhận `403 identity.user_deleted` nếu gửi request bằng access token còn hạn (≤ 5 phút sau khi xoá).
  Mở app sau đó thì refresh bị Keycloak từ chối (tài khoản đã mất) — không phân biệt được với "hết phiên" — nên sổ
  (đã mã hoá) nằm lại theo B7 cho tới khi user bấm "Xoá dữ liệu trên máy này", đăng nhập tài khoản khác (B5) hoặc gỡ app.
- Tombstone đã dọn thì op UPSERT tới muộn (máy bỏ xó > 90 ngày, đăng nhập lại và gửi outbox trước khi pull) tạo lại
  bản ghi đã xoá ở máy khác: "xoá thắng" chỉ giữ được trong 90 ngày. Không mất dữ liệu, user xoá lại được. Sửa triệt
  để cần op mang `baseSeq` (hợp đồng mới, cả hai repo) — chưa làm.

## References

- App Store Review Guidelines 5.1.1(v) · Google Play "Account deletion" · Nghị định 13/2023/NĐ-CP Điều 16
- OWASP ASVS 4.0 V2.2, V1.4 · OWASP MASVS v2 (AUTH-2, STORAGE-1/2, PLATFORM-3) · RFC 9110 §15.3.3 · RFC 9457
- ADR-002 (sync, tombstone), ADR-003 (outbox), ADR-004 (Keycloak), ADR-006 (B5–B7)
