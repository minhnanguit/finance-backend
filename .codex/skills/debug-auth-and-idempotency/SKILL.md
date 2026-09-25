---
name: debug-auth-and-idempotency
description: Debug luồng xác thực (token Keycloak, 401/403, JIT provisioning) và Idempotency-Key filter trong finance-backend. Dùng khi gặp 401 với token Keycloak, sai issuer/audience, user bị tạo trùng, 400 idempotency_key_invalid, 409/422 khi replay request.
---

# Debug auth & idempotency

Quyết định nền: `docs/adr/ADR-004-auth-keycloak.md`. Code: `shared/security/`, `shared/web/idempotency/`, `modules/identity/`.
Hạ tầng: `deploy/keycloak/README.md`, `make kc`.

## Luồng auth hiện tại (Keycloak, ADR-004)

Backend **không phát hành token**. Thứ tự khi một request có `Authorization: Bearer ...`:

1. `JwtDecoder` (bean trong `SecurityConfiguration`) fetch JWKS từ `app.security.jwk-set-uri`, verify signature.
2. `JwtValidators.createDefaultWithIssuer` kiểm `iss` == `app.security.issuer-uri` và `exp`.
3. `AudienceValidator` kiểm `aud` chứa `app.security.audience`.
4. `ProvisioningJwtAuthenticationConverter` → `CurrentUserResolver.resolve(SubjectClaims)`.
5. `ProvisioningCurrentUserResolver` tra cache Caffeine theo `sub`; miss thì gọi `ProvisionUserUseCase`.
6. `AuthenticatedUser.requireCurrent()` trả về **`users.id` nội bộ**, không phải `sub`.

### 401 thì soi theo thứ tự này

| Triệu chứng | Nguyên nhân hay gặp |
|---|---|
| 401 với mọi token | `aud` thiếu `finance-api` → audience mapper trong realm bị xoá |
| 401 sau khi đổi `KC_HOSTNAME` | `iss` trong token cũ ≠ `app.security.issuer-uri` |
| App khởi động lỗi / treo lúc gọi API | `jwk-set-uri` không reachable từ process này (nhớ: khác `issuer-uri` ở local) |
| Đổi tên/email trên Keycloak mà `/me` chưa đổi | Cache `app.identity.subject-cache-ttl` (mặc định 10 phút) chưa hết hạn |
| Tạo ra 2 dòng `users` cho cùng 1 người | Sai: `ux_users_external_subject` phải chặn. Kiểm `ON CONFLICT` trong `UserJpaRepository` |

Không còn `/api/v1/auth/*`, không còn `refresh_tokens`, không còn `password_hash`.
Đăng nhập, đổi mật khẩu, reset mật khẩu đều diễn ra ở Keycloak — xem `deploy/keycloak/README.md`.

## Triệu chứng → chỗ cần nhìn

| Triệu chứng | Nghi ngờ |
|---|---|
| 401 trên endpoint đáng lẽ public | Thiếu khai trong `PublicEndpoints` (spec có `security: []` là chưa đủ) |
| 401 sau ~5 phút | Đúng thiết kế: access token Keycloak sống 300s. Client tự refresh với Keycloak, backend không liên quan |
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

curl -s -i localhost:8080/api/v1/me          # không token → 401, code auth.unauthenticated
curl -s localhost:8081/realms/finance/.well-known/openid-configuration | jq .issuer   # phải là http://10.0.2.2:8081/realms/finance
```

Hiện chưa có endpoint ghi nào, nên muốn thử idempotency bằng tay thì phải chờ module nghiệp vụ đầu tiên.

Test có sẵn để đọc/chạy: `src/integrationTest/…/JitProvisioningIT.java` (8 case), `src/test/…/idempotency/IdempotencyFilterTest.java`, `ProvisionUserServiceTest`.

## Nguyên tắc khi sửa

- Không log token hay email đầy đủ.
- Đổi hành vi auth/idempotency ⇒ cập nhật `api/openapi.yaml` (mô tả header, mã lỗi) và thêm IT tương ứng.
- Không hạ `SessionCreationPolicy.STATELESS`, không bật lại form login / http basic / csrf session.
