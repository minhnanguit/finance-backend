# Keycloak realm as code

`realm-finance.json` là **nguồn sự thật** cho cấu hình Keycloak (ADR-004, quyết định #5).
Không chỉnh trên Admin Console rồi để đó — sửa file này, `make reset && make up`, commit.

> ⚠️ Importer của Keycloak **từ chối field lạ**. Không thêm khoá `_comment` vào JSON — file sẽ không import được. Mọi giải thích nằm ở đây.

## Vì sao mỗi giá trị lại như vậy

| Khoá | Giá trị | Lý do |
|---|---|---|
| `accessTokenLifespan` | `300` (5 phút) | ADR-004. Ngắn để token lộ cũng hết hạn nhanh |
| `ssoSessionIdleTimeout` / `MaxLifespan` | `2592000` (30 ngày) | Vòng đời refresh token |
| `revokeRefreshToken` + `refreshTokenMaxReuse: 0` | bật / 0 | Rotation. Xem mục "Rotation bảo vệ tới đâu" bên dưới — **không mạnh như tên gọi** |
| `bruteForceProtected` | `true` | Lấp lỗ hổng không có rate limit của bản auth tự viết |
| `permanentLockout` | `false` | Khoá vĩnh viễn để kẻ tấn công tự DoS user thật |
| `failureFactor: 5`, `maxFailureWaitSeconds: 900` | | Sai 5 lần → khoá tạm, tăng dần tới tối đa 15 phút |
| `passwordPolicy` | `length(12) ... argon2` | Argon2 là mặc định hiện đại của Keycloak, mạnh hơn bcrypt |
| `verifyEmail` | `true` | User mới **phải** xác nhận email mới đăng nhập được. Dev đọc mail ở http://localhost:8025 |
| `registrationEmailAsUsername` | `true` | App không có khái niệm username riêng |
| `smtpServer.host` | `mailpit` | Tên service trong docker-compose. Production phải đổi sang SMTP thật |

## Rotation bảo vệ tới đâu (đã đo thật, 2026-09-20)

`revokeRefreshToken=true` + `refreshTokenMaxReuse=0` **không** phải reuse detection đầy đủ theo
kiểu OAuth 2.1. Keycloak chỉ nhớ **một** refresh token "hiện hành". Kết quả đo được:

| Tình huống | Kết quả |
|---|---|
| Dùng lại **đúng token vừa dùng** (RT1 → RT2, rồi RT1 lần nữa) | ✅ Bị chặn **và huỷ cả session** — RT2 cũng chết theo |
| Dùng token **cũ hơn** sau khi đã xoay thêm (RT1 → RT2 → RT3, rồi RT1) | ⚠️ **Được chấp nhận** |
| Replay authorization code | ✅ Bị chặn và huỷ session |

**Nghĩa là gì trong thực tế:** kịch bản trộm token phổ biến nhất *có* bị bắt — kẻ trộm lấy token
hiện hành, rồi hoặc nó hoặc client thật dùng trước, lần dùng của bên kia thành "dùng lại đúng token
vừa dùng" → session chết. Nhưng một token **cũ** lọt ra ngoài (qua log, backup, crash dump) thì
vẫn dùng được cho tới khi session hết hạn — hiện là **30 ngày**.

Giảm thiểu: tuyệt đối không log token (đã là luật trong `coding-standards.md`), và cân nhắc
rút `ssoSessionIdleTimeout` nếu thấy 30 ngày quá rộng.

## Hai chỗ dễ sai nhất

**1. Audience mapper — thiếu là hỏng tất cả.**
Keycloak **không** tự bỏ `finance-api` vào claim `aud`. Mapper `finance-api-audience` trên client
`finance-mobile` làm việc đó. Xoá nhầm nó → backend từ chối sạch token với 401 mà log không nói rõ lý do.
Client `finance-api` tồn tại **chỉ** để làm giá trị audience, không bật flow nào.

**2. `directAccessGrantsEnabled` phải là `false`.**
Bật lên là mở Resource Owner Password Grant — deprecated, OAuth 2.1 đã loại bỏ, MFA không chạy đúng.
ADR-004 quyết định #3 cấm dùng. Đừng bật để "cho tiện test".

## Hostname

**Có tunnel** (`make tunnel`): `KC_HOSTNAME` = URL HTTPS `*.trycloudflare.com` (lấy từ `deploy/.env`), dùng
chung cho emulator, simulator và máy thật. Caddy (`deploy/edge/Caddyfile`) chỉ public `/realms/*` và
`/resources/*`; `/admin` và realm `master` trả 404 — Admin Console vẫn chỉ ở `http://localhost:8081/admin`.

**Không tunnel** (mặc định):

`KC_HOSTNAME=http://10.0.2.2:8081` đặt ở `docker-compose.yml`, **không** ở file này.
Issuer nằm trong token nên hostname phải cố định từ dev. `10.0.2.2` là host loopback nhìn từ
Android emulator, nên token phát ra dùng được cho cả app lẫn backend.

Backend chạy trên host thì tải JWKS qua `localhost:8081` (xem `KEYCLOAK_JWK_SET_URI` trong `.env.example`) —
`KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true` cho phép việc này.

## Export lại sau khi chỉnh trên Console

```bash
docker compose -f deploy/docker-compose.yml exec keycloak \
  /opt/keycloak/bin/kc.sh export --dir /tmp/export --realm finance --users skip
docker compose -f deploy/docker-compose.yml cp \
  keycloak:/tmp/export/finance-realm.json deploy/keycloak/realm-finance.json
```

Bản export đầy đủ rất dài và có nhiều giá trị mặc định. Nên chỉ chép **phần thực sự đổi** vào file
gọn này, để diff còn đọc được.

## Lệnh hay dùng

| Lệnh | Việc |
|---|---|
| `make kc` | Mở Admin Console (`admin` / `admin`) |
| `make mail` | Mở Mailpit đọc mail verify / reset password |
| `make kc-db` | Tạo database `keycloak` khi volume Postgres đã có sẵn |
| `make reset && make up` | Nạp lại realm từ đầu sau khi sửa JSON |
