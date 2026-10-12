# ADR-004 – Keycloak self-host là IdP duy nhất, backend là resource server thuần

**Status:** accepted · 2026-09-20

## Context

App tài chính cần: reset mật khẩu, verify email, chống brute-force, MFA, và về sau là đăng nhập Google/Apple. Tự viết hết là nhiều việc và nhiều rủi ro bảo mật. Hai lựa chọn được cân: **Auth0** (SaaS) và **Keycloak self-host**.

Thời điểm đổi là lúc rẻ nhất — hệ thống đang 0 user, không phải migrate password hash.

## Decision

Dùng **Keycloak self-host** làm IdP duy nhất. Backend **không còn phát hành token**.

| # | Quyết định |
|---|---|
| 1 | Keycloak là IdP duy nhất. Bỏ hoàn toàn password hash và refresh token khỏi `finance-backend` |
| 2 | Backend là **OAuth2 Resource Server thuần**: verify JWT qua JWKS của Keycloak, bắt buộc validate `aud = finance-api` |
| 3 | Mobile là **public client**, dùng **Authorization Code + PKCE (S256)**, mở system browser. Không WebView, không client secret, **không** Resource Owner Password Grant (deprecated, OAuth 2.1 đã loại bỏ) |
| 4 | **`user_id` của mọi bảng nghiệp vụ mang giá trị `users.id` nội bộ.** `sub` của Keycloak chỉ nằm ở cột `users.external_subject` (UNIQUE). Bảng của module khác **không đặt FK constraint** sang `users` (sửa 2026-09-29, ADR-005) |
| 5 | Realm cấu hình dạng **file JSON commit trong repo** (`deploy/keycloak/realm-finance.json`), không click trên Admin UI |
| 6 | **Không gọi Keycloak Admin API trong request path.** Cùng nguyên tắc với luật "RabbitMQ không nằm trong request path" |
| 7 | User local tạo bằng **JIT provisioning**: lần đầu thấy `sub` mới thì `INSERT ... ON CONFLICT DO NOTHING`, có cache in-memory để không đụng DB mỗi request |

### Tham số chốt

| Thông số | Giá trị |
|---|---|
| Realm | `finance` |
| Client mobile | `finance-mobile` — public, standard flow, PKCE S256 bắt buộc |
| Audience | `finance-api` (phải thêm **audience mapper** trong realm, Keycloak không tự bỏ vào) |
| Redirect URI | `com.uit.finance://oauth/callback` (thêm App Links / Universal Links sau) |
| Access token TTL | 5 phút |
| Refresh token TTL | 30 ngày, bật rotation (`revokeRefreshToken`, `refreshTokenMaxReuse=0`) |
| Khoá định danh | claim `sub`. **Không dùng email** — email đổi được |
| Cổng local | `8081` (backend giữ `8080`) |

### Bật sẵn trong realm

Brute force detection · password policy (≥ 12 ký tự, không trùng username, lịch sử) · verify email · reset password · TOTP (optional) · WebAuthn/passkey.

## Why Keycloak, not Auth0

| Lý do | |
|---|---|
| Chi phí | Keycloak 0đ. Auth0 tính phí cho **custom domain** — app tài chính redirect sang `xxx.auth0.com` thì mất tin tưởng |
| Hạ tầng | Đã tự vận hành Postgres + Redis + RabbitMQ. Keycloak là +1 container dùng chung Postgres sẵn có, không phải mô hình vận hành mới |
| Latency | Đặt server VN/SG. Auth0 region gần nhất là JP/AU |
| Dữ liệu | User nằm trong DB của mình. Liên quan Nghị định 13/2023 về bảo vệ dữ liệu cá nhân |
| Đảo ngược rẻ | Cả hai đều OIDC chuẩn → code mobile và backend y hệt. Đổi sau chỉ là đổi `issuer-uri` + migrate user |

## Consequences

**Được:**
- Reset password, verify email, chống brute-force, TOTP, passkey — có ngay, không phải viết.
- `modules.identity` co từ 58 file xuống ~15. Không còn giữ password hash → giảm hẳn bề mặt rủi ro.
- `shared.security` đơn giản hơn: chỉ decode, không encode. Xoá `JwtKeyConfiguration`, `PemKeys`, `JwtProperties`.
- Thêm Google/Apple sign-in sau này là cấu hình, không phải code.

**Giới hạn đã đo thật (2026-09-20, Keycloak 26.7.4):**
- Rotation của Keycloak **không** phải reuse detection đầy đủ. Nó chỉ nhớ một token hiện hành:
  dùng lại **đúng token vừa dùng** thì bị chặn và huỷ session ✅, nhưng dùng một token **cũ hơn**
  trong chuỗi (sau khi đã xoay thêm lần nữa) thì **vẫn được chấp nhận** ⚠️.
- Hệ quả: token cũ lọt ra ngoài vẫn dùng được tới khi session hết hạn (30 ngày). Kịch bản trộm
  token hiện hành thì vẫn bị bắt. Chi tiết và cách giảm thiểu ở `deploy/keycloak/README.md`.

**Mất / phải chấp nhận:**
- ⚠️ **Tự chịu trách nhiệm vá CVE Keycloak.** Keycloak ra bản mới ~mỗi quý và đã từng có CVE nghiêm trọng. Phải đặt lịch nâng cấp hàng quý — đây là cái giá của self-host.
- Không có phát hiện mật khẩu đã lộ (HaveIBeenPwned) như Auth0.
- Không có OTP qua SMS dựng sẵn; cần thì phải viết extension + thuê provider.
- Mobile mất màn hình Login/Register native — đăng nhập diễn ra trong browser (đánh đổi bắt buộc của RFC 8252).
- Thêm một deployable phải backup, giám sát, và cố định hostname.

**Ràng buộc kéo dài:**
- Không bảng nghiệp vụ nào được lưu hay trỏ vào `sub` của Keycloak. Vi phạm điều này thì đổi IdP sau phải migrate cả DB.
- `KC_HOSTNAME` phải cố định ngay từ môi trường dev. Issuer nằm trong token; đổi sau làm hỏng mọi token đang lưu.

## References

- Plan triển khai 6 phase: `docs/AUTH-KEYCLOAK-PLAN.md` (root repo)
- RFC 8252 – OAuth 2.0 for Native Apps
- ADR-002 (sync) dùng `users.id` nội bộ làm `user_id`, khớp với quyết định #4 ở trên
