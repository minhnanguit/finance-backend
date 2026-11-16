# Chạy production trên một server

Plan tổng: `../../docs/DEPLOY-PLAN.md` · CI/CD: `../../docs/CICD-PLAN.md`. Server là một máy Ubuntu 24.04 ARM64
(tối thiểu 4 GB RAM) có IP công khai — Oracle Always Free hoặc VPS thuê đều như nhau.

```
internet ──443──▶ caddy ──/realms, /resources──▶ keycloak ──▶ postgres (db keycloak)
                    └──────/api, /actuator/health──▶ app ──▶ postgres (db finance), redis, rabbitmq
```

## Cách deploy

```
git tag v2.1.0 && git push origin v2.1.0
  → CI: test ─▶ build image ARM64 ─▶ GHCR ─▶ SSH vào server ─▶ deploy/release.sh v2.1.0
```

Server **không có mã nguồn**, chỉ có `Makefile` + `deploy/` (CI chép theo tag), `deploy/.env.prod` (secret) và
`deploy/.release` (tag đang chạy). `release.sh`: backup DB → kéo image → bật bản mới, chờ healthy → đồng bộ realm →
gọi health HTTPS → hỏng ở đâu thì **tự quay về bản trước** và báo đỏ trên GitHub.

| Việc | Lệnh (chạy trên Mac, thư mục `finance-backend`) |
|---|---|
| Phát hành bản mới | Đẩy tag `vX.Y.Z` (bản thử: `vX.Y.Z-rc.N`) |
| Deploy lại / lùi về bản cũ | `make srv-deploy TAG=v2.0.0` (hoặc GitHub → Actions → deploy → Run workflow) |
| Log / Admin Console | `make srv-logs S=app` / `make srv-admin` |
| Bản đang chạy | `ssh finance-prod cat finance-backend/deploy/.release` |

**Migration phải tương thích ngược:** lùi image không lùi schema (Flyway chạy khi app khởi động). Xoá/đổi tên cột thì
tách nhiều release — xem skill `write-migration`. Mỗi lần deploy đều backup trước.

## 0. Dựng server lần đầu (từ Mac)

Thêm alias SSH vào `~/.ssh/config` của Mac:

```
Host finance-prod
  HostName <IP server>
  User ubuntu
  IdentityFile ~/.ssh/finance_oracle
```

DNS: trỏ `DOMAIN` (ví dụ `xxx.duckdns.org`) về IP server **trước** lần deploy đầu, nếu không Caddy không xin được
chứng chỉ.

| Bước | Lệnh |
|---|---|
| 1. Cài Docker, mở 80/443 trong máy, múi giờ, cron backup (`deploy/server-bootstrap.sh`) | `make srv-setup` |
| 2. Chép `Makefile` + `deploy/` lên (chưa có mã nguồn, chỉ để có mẫu `.env.prod`) | `make srv-sync` |
| 3. Sinh secret + mở `.env.prod` để dán SMTP Brevo | `make srv-env DOMAIN=<domain>` |
| 4. Tạo SSH key riêng cho CI, cài lên server, nạp 4 secret `DEPLOY_*` vào repo (cần `gh auth login`) | `make srv-ci` |
| 5. Deploy lần đầu | Đẩy tag, hoặc `make srv-deploy TAG=<tag đã có image>` |

Key CI được cài với quyền `restrict` (chỉ chạy lệnh, không forward/pty) — lộ key thì xoá dòng `finance-ci-deploy`
trong `~/.ssh/authorized_keys` trên server rồi chạy lại `make srv-ci`.

Mục 1–4 bên dưới là những gì các lệnh trên làm, để làm tay khi cần.

## 1. Chuẩn bị server (một lần)

```bash
# Docker
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && newgrp docker

# Mở 80/443. Image Ubuntu của Oracle có sẵn luật iptables REJECT — phải chèn TRƯỚC luật đó.
# Ngoài ra mở 80/443 (TCP, nguồn 0.0.0.0/0) ở Security List / firewall của nhà cung cấp.
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

## 2. Secret

```bash
cd ~/finance-backend
make prod-secrets          # tạo deploy/.env.prod (chmod 600), sinh ngẫu nhiên mọi mật khẩu
nano deploy/.env.prod      # điền DOMAIN, SMTP_USER, SMTP_PASSWORD, SMTP_FROM
```

`deploy/.env.prod` là bản duy nhất của các mật khẩu — **sao lưu nó ra ngoài server** (password manager).
Mất file này thì backup DB vẫn khôi phục được, nhưng phải đặt lại mật khẩu. CI không bao giờ đọc hay ghi file này.

## 3. Lệnh trên server

| Lệnh | Việc |
|---|---|
| `deploy/release.sh v2.1.0` | Deploy một tag (cần `docker login ghcr.io` trước — CI tự làm) |
| `make prod-health` | Gọi `https://<DOMAIN>/actuator/health` |
| `make prod-ps` / `make prod-logs S=app` | Trạng thái / log |
| `make prod-down` | Tắt, giữ dữ liệu |

Kiểm tra sau lần đầu: health trả `UP` · `https://<DOMAIN>/realms/finance/.well-known/openid-configuration` có
`"issuer":"https://<DOMAIN>/realms/finance"` · `https://<DOMAIN>/admin` trả 404 · đăng ký tài khoản trên app nhận được
mail (lần đầu xem cả hộp Spam).

Thử stack production **trên Mac** trước khi phát hành: `make prod-secrets` (rồi đặt `DOMAIN=localhost`) và
`make prod-up` — build image từ mã nguồn (`IMAGE_TAG=local`).

## 4. Backup

```bash
make prod-backup           # pg_dumpall cả hai DB vào ~/finance-backups, giữ 7 bản; dump không trọn vẹn ⇒ báo lỗi
# Cron 03:15 hằng ngày do srv-setup cài:
# 15 3 * * * cd ~/finance-backend && make prod-backup >> ~/finance-backups/backup.log 2>&1
```

Khôi phục vào stack trắng: `gunzip -c <file>.sql.gz | docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.prod --env-file deploy/.release exec -T postgres psql -U finance -d postgres`.

Backup nằm cùng server — thỉnh thoảng `scp` một bản về máy cá nhân.

## 5. Keycloak Admin Console

Không mở ra internet. Từ Mac: `make srv-admin`, rồi mở http://localhost:8082/admin — tài khoản `admin`, mật khẩu
`KC_ADMIN_PASSWORD` trong `deploy/.env.prod` trên server.
