---
name: debug-auth-and-idempotency
description: Debug luồng xác thực (JWT RS256, refresh token rotation, 401/403) và Idempotency-Key filter trong finance-backend. Dùng khi gặp lỗi đăng nhập, token hết hạn, session bị revoke, 400 idempotency_key_invalid, 409/422 khi replay request.
---

# Debug auth & idempotency

Quyết định nền: `docs/adr/ADR-004-auth-keycloak.md`. Code: `shared/security/`, `shared/web/idempotency/`, `modules/identity/`.

> ⚠️ **Phần auth của skill này đã lỗi thời.** Hệ thống đang migrate sang Keycloak (ADR-004 mới).
> Mô tả JWT tự phát hành / refresh token rotation bên dưới chỉ còn đúng cho tới khi Phase 2 của
> `docs/AUTH-KEYCLOAK-PLAN.md` hoàn tất. Phần idempotency vẫn đúng.

## Bản đồ luồng auth

```
POST /api/v1/auth/register|login  → RegisterUserService / AuthenticateUserService
                                   → SessionIssuer.open(user, device, now)
                                   → JwtAccessTokenIssuer (RS256, ttl 15m, sub=userId, did=deviceId)
                                   → SecureRandomRefreshTokenGenerator (opaque 256-bit, DB lưu SHA-256)
POST /api/v1/auth/refresh         → RefreshSessionService: rotate (token cũ revoke, cấp cặp mới)
                                     reuse token đã rotate → revoke TOÀN BỘ session của device
POST /api/v1/auth/logout          → RevokeSessionService (cần bearer token)
GET  /api/v1/me                   → MeController → UserProfileService
```

Endpoint public: `POST /auth/register|login|refresh`, `GET /actuator/health*`, `/actuator/info`, `/openapi.yaml`, `/swagger-ui*`, `/v3/api-docs/**` — danh sách duy nhất ở `shared/security/PublicEndpoints`.

## Triệu chứng → chỗ cần nhìn

| Triệu chứng | Nghi ngờ |
|---|---|
| 401 trên endpoint đáng lẽ public | Thiếu khai trong `PublicEndpoints` (spec có `security: []` là chưa đủ) |
| 401 với token vừa lấy | Khoá RSA đổi giữa hai lần chạy — không set `JWT_PRIVATE_KEY_PEM`/`JWT_PUBLIC_KEY_PEM` thì app sinh cặp **ephemeral** mỗi lần khởi động (có log warning). Xem `JwtKeyConfiguration`, `PemKeys` |
| 401 sau ~15 phút | Đúng thiết kế: `app.security.jwt.access-token-ttl=15m`. Client phải gọi `/auth/refresh` |
| Refresh trả lỗi và mọi phiên chết | Reuse detection: refresh token dùng **một lần**; trình token đã rotate → revoke toàn bộ session của device đó |
| 403 | Đã xác thực nhưng thiếu quyền — `ProblemAuthenticationEntryPoint` xử lý cả 401 và 403 |
| Body lỗi không phải JSON | Kiểm tra `ProblemDetailsExceptionHandler` / `ProblemDetailWriter`; `server.error.include-stacktrace=never` |

## Idempotency (`IdempotencyFilter`)

Áp dụng cho POST/PUT/PATCH có path bắt đầu bằng prefix trong `app.idempotency.protected-path-prefixes` (mặc định `/api/`).

| Tình huống | Kết quả |
|---|---|
| Thiếu header hoặc không phải UUID | 400 `request.idempotency_key_invalid` |
| Cùng key, cùng body, request đầu **đang chạy** | 409 |
| Cùng key, **khác** body | 422 |
| Cùng key, cùng body, đã xong | Replay response đã lưu + header `Idempotency-Replayed: true` |
| Body > `app.idempotency.max-body-bytes` (1 MiB) | 413 `request.too_large` |
| Response 5xx | **Không** lưu, client được phép retry |

Key scope theo (principal | "anonymous", method, path). Store là Redis, TTL `app.idempotency.ttl=24h`, in-progress `30s`.
Filter đăng ký **sau** `AuthorizationFilter` để scope được theo user đã xác thực.

## Lệnh kiểm tra nhanh

```bash
make up
make run

KEY=$(uuidgen)
curl -s -i -X POST localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" \
  -d '{"email":"alice@example.com","password":"correct horse battery","displayName":"Alice",
       "device":{"deviceId":"dev-00000001","deviceName":"Pixel 9","platform":"ANDROID"}}'

# gọi lại đúng lệnh trên → phải thấy Idempotency-Replayed: true
```

Test có sẵn để đọc/chạy: `src/integrationTest/…/AuthFlowIT.java` (8 case), `src/test/…/idempotency/IdempotencyFilterTest.java`, `RefreshSessionServiceTest`.

## Nguyên tắc khi sửa

- Không log token, password, email đầy đủ.
- Đổi hành vi auth/idempotency ⇒ cập nhật `api/openapi.yaml` (mô tả header, mã lỗi) và thêm IT tương ứng.
- Không hạ `SessionCreationPolicy.STATELESS`, không bật lại form login / http basic / csrf session.
