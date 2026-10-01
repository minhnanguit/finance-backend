# ADR-006 – Bảo mật dữ liệu sổ thu chi: cô lập theo user, giới hạn, mã hoá trên máy

**Status:** accepted · 2026-09-30 · làm trong từng phase của `docs/LEDGER-PLAN.md`, không để dành

## Context

Ledger là dữ liệu nhạy cảm nhất của app: ai tiêu gì, ở đâu, bao nhiêu. Có 2 bề mặt tấn công:

- **API sync**: một cửa ghi duy nhất (ADR-002 S1), nhận id do client sinh. Lỗi số 1 của OWASP API Top 10 là **API1 BOLA/IDOR**: gửi id của người khác để đọc hoặc sửa.
- **Điện thoại**: offline-first nên toàn bộ lịch sử nằm trên máy. Mất máy, máy root/jailbreak, hoặc 2 người dùng chung một máy.

Thước đo: **OWASP API Security Top 10 (2023)** cho backend, **OWASP MASVS v2** cho mobile, **ASVS V7** cho log.

## Decision

### Backend

| # | Quyết định | Chặn | Chuẩn |
|---|---|---|---|
| B1 | Mọi truy vấn và mọi lần ghi đều kèm `user_id = :me`, kể cả sửa theo id | A đọc/sửa sổ của B bằng id của B | API1 |
| B2 | Không phải của mình → **"không tồn tại"**, không bao giờ "không có quyền" | Dò xem id nào tồn tại | API1 |
| B3 | User **chỉ lấy từ token**. `user_id`, `change_seq`, `version`, `created_at`, `updated_at`, `deleted_at`, `archived_at` do server điền. Field lạ → từ chối | Client tự gán dữ liệu cho người khác, phá thứ tự sync | API3 |
| B4 | Giới hạn tần suất, kích thước request, độ dài chữ, số tiền, số ví/danh mục | Spam làm đầy DB, làm chậm server | API4 |
| B8 | Log chỉ ghi **id, số lượng, outcome, mã lỗi**. Không ghi số tiền, ghi chú, người nhận, tên ví | Lộ dữ liệu qua log, Sentry, báo lỗi | MASVS-PRIVACY |
| B9 | Mỗi op sync lưu **máy nào, lúc nào, entity nào, kết quả gì** trong 30 ngày (`sync_ops`) | Không truy được ai đổi dữ liệu khi có sự cố | ASVS V7 |

### Mobile

| # | Quyết định | Chặn | Chuẩn |
|---|---|---|---|
| B5 | **Mỗi user một file DB**: `finance-<userId>.db`. Đăng xuất: còn op chưa gửi thì cảnh báo, rồi xoá file + khoá | Dữ liệu của A lên tài khoản B, hoặc B xem sổ của A trên cùng máy | MASVS-STORAGE |
| B6 | **DB mã hoá SQLCipher**, khoá 256-bit ngẫu nhiên cho mỗi file, cất trong `SecureStorage` (Keystore / Keychain) | Mất máy, root/jailbreak đọc lịch sử chi tiêu | MASVS-STORAGE, MASVS-CRYPTO |
| B7 | Hết phiên (refresh token hết hạn, offline > 30 ngày) **chỉ xoá token, giữ file DB**. Đăng nhập lại đúng user thì gửi tiếp | Mất các khoản chưa kịp gửi | MASVS-STORAGE |

## Cách làm cụ thể

### B1, B2: cô lập theo user

| Chỗ | Cách |
|---|---|
| Port out ledger | **Mọi method trong `ledger.application.port.out` có tham số `UserId`**. ArchUnit **luật #10** bắt, quên là build fail |
| Spring Data | Không dùng `findById(id)` trần. Mọi query là `findByIdAndUserId`, `...WhereUserId...` |
| Upsert | `INSERT ... ON CONFLICT (id) DO UPDATE SET ... WHERE <bảng>.user_id = :me`. 0 dòng bị ảnh hưởng = id của người khác → `REJECTED ledger.not_found`, **không ghi đè** |
| Tham chiếu ví/danh mục | Load theo `(id, user_id)`. Không thấy → `ledger.reference_pending` → `RETRY`, **giống hệt** trường hợp ví chưa sync tới (cùng mã, cùng message). Kẻ xấu không phân biệt được "chưa tới" với "của người khác" |
| Tham chiếu tới bản ghi của mình đã xoá | `REJECTED ledger.not_found`. Không lộ gì vì chỉ áp cho dữ liệu của chính user |
| Pull | `WHERE user_id = :me AND change_seq > :since`. Không có tham số nào khác chọn user |

Xử lý của B2 lan ra mọi REST đọc sau này: id không phải của mình → `404`, không bao giờ `403`.

### B3: chỉ nhận field nghiệp vụ

- Contract: schema `data` của từng entity bật `additionalProperties: false`, không khai field hệ thống.
- Runtime: Jackson `FAIL_ON_UNKNOWN_PROPERTIES = true` cho DTO sync. Chặn ở 2 lớp để không phụ thuộc cấu hình generator.
- `userId` của mọi command lấy từ `AuthenticatedUser` (token), không có trong DTO.

### B4: giới hạn

| Mục | Giới hạn | Chặn ở |
|---|---|---|
| Push | 60 request/phút mỗi user | `RateLimitFilter` (`shared.web`, Redis) → `429` + `Retry-After` |
| Pull | 120 request/phút mỗi user | như trên |
| Body push | 256 KB | Filter theo path, trước khi đọc JSON → `413` |
| Op mỗi batch | 100 | Contract (`maxItems`) + `modules.sync` |
| `limit` pull | 500 | Contract (`maximum`) |
| Số tiền, độ dài chữ, ngày | ADR-005 §4 | Domain + `CHECK` trong DB |
| Ví / danh mục mỗi user | 50 / 300 | Domain (`ledger.limit_exceeded`) |

Rate limit dùng Redis có sẵn, 0 dependency mới. Redis chết → **cho qua** (fail-open) và log cảnh báo: rate limit là bảo vệ tài nguyên, không phải kiểm soát truy cập, và không được làm sập sync.

### B5, B6, B7: DB trên điện thoại

| Việc | Cách |
|---|---|
| Mở DB | Chỉ sau khi có `userId` từ `/me`. Koin scope theo phiên đăng nhập; đóng scope là đóng DB |
| Khoá | 256-bit từ CSPRNG, sinh khi tạo file, key `db-key-<userId>` trong `SecureStorage`. Không bao giờ ra khỏi máy |
| iOS Keychain | `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`: sync nền chạy được sau lần mở khoá đầu, khoá không theo iCloud/backup |
| Android | Khoá DB được mã hoá bằng khoá AES-GCM trong Keystore (cơ chế `SecureStorage` hiện có). `allowBackup=false` cho file DB |
| `outbox`, `sync_cursor` | Chuyển vào DB của user. Không còn bảng dùng chung giữa các user |
| Đăng xuất (user bấm) | Còn op chưa gửi → hộp thoại cảnh báo số lượng → xoá file + khoá + token |
| Hết phiên (B7) | Chỉ xoá token. Giữ file + khoá. Đăng nhập lại cùng `userId` → mở lại DB, gửi tiếp outbox |
| User khác đăng nhập vào máy còn file của người cũ | Hỏi xác nhận rồi xoá file của người cũ. Không bao giờ mở DB của user A dưới phiên user B |

### B8: log

| Được ghi | Không được ghi |
|---|---|
| `opId`, `entity`, `entityId`, `outcome`, `code`, số lượng op, thời gian xử lý, `userId` nội bộ | `amount_minor`, `opening_balance_minor`, `note`, `payee`, tên ví/danh mục, body request/response |

- Backend: exception của ledger không nhét giá trị field vào message (`ledger.invalid_field` chỉ kèm tên field).
- Event ledger chỉ mang id (ADR-005 §8).
- Mobile: Konsist cấm log `note`, `payee`, `amount` (Phase 4).

## Consequences

**Được**
- IDOR bị chặn ở 3 lớp: port bắt buộc `UserId` (build), query/upsert có `user_id` (runtime), test tấn công (CI).
- Không dò được id: "của người khác" và "chưa tới" trả cùng một response.
- Mất máy không lộ sổ. Dùng chung máy không lẫn sổ. Hết phiên không mất dữ liệu.

**Chấp nhận**
- SQLCipher tăng kích thước app (~3–4 MB mỗi ABI) và chậm hơn SQLite thường vài %. CI macOS phải link SQLCipher cho iOS.
- Rate limit fail-open: Redis chết thì tạm thời không có giới hạn tần suất.
- Đăng xuất khi còn op chưa gửi mà user vẫn xác nhận → các op đó mất. Đó là lựa chọn có cảnh báo của user.
- Chưa có: xoá tài khoản, PIN/biometric, che màn hình, chặn chụp màn hình (plan #14); certificate pinning (`AUTH-KEYCLOAK-PLAN.md` Phase 6B).

## References

- OWASP API Security Top 10 2023 · OWASP MASVS v2 · OWASP ASVS 4.0 V7
- ADR-002 (sync), ADR-004 (user id nội bộ), ADR-005 (giới hạn, event)
- `docs/LEDGER-PLAN.md` mục 2.3, 4.3
