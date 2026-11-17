# Mỗi target làm đúng MỘT việc. Muốn chạy nối tiếp thì liệt kê nhiều target:
#   make up run          (bật hạ tầng rồi chạy app)
# Gõ `make` hoặc `make help` để xem danh sách.

COMPOSE  := docker compose -f deploy/docker-compose.yml
GRADLE   := ./gradlew
BASE_URL ?= http://localhost:18473

# Khi `make tunnel` đang bật, deploy/.env chứa KC_PUBLIC_URL (HTTPS của Cloudflare Tunnel). Backend phải
# validate đúng issuer đó. Chỉ export khi có giá trị: biến rỗng sẽ đè mất default trong application.yml.
-include deploy/.env
ifneq ($(strip $(KC_PUBLIC_URL)),)
export KEYCLOAK_ISSUER_URI := $(KC_PUBLIC_URL)/realms/finance
endif

.DEFAULT_GOAL := help
.PHONY: help up down reset ps logs run health swagger rabbit kc kc-db mail tunnel tunnel-off tunnel-url test itest build fmt lint api clean \
        prod-secrets prod-up prod-down prod-ps prod-logs prod-health prod-backup \
        srv-setup srv-sync srv-env srv-smtp-key srv-ci srv-deploy srv-logs srv-admin

help: ## Liệt kê mọi lệnh make của repo này
	@echo "finance-backend — các lệnh có sẵn:"
	@grep -hE '^[a-z][a-zA-Z_-]*:.*## ' $(MAKEFILE_LIST) \
	  | awk 'BEGIN{FS=":.*## "}{printf "  \033[36m%-11s\033[0m %s\n", $$1, $$2}'

# ---------- Hạ tầng (Docker) ----------

up: ## Bật Postgres + Redis + RabbitMQ + Keycloak + Mailpit, chờ tới khi healthy
	@# Secret của client finance-backend (quyền manage-users, ADR-008 A8): sinh ngẫu nhiên MỘT lần cho máy này, giữ
	@# trong deploy/.env (gitignore). Keycloak nhận lúc import realm; `make run` truyền cùng giá trị cho backend.
	@grep -qs '^KC_FINANCE_BACKEND_SECRET=' deploy/.env || { \
	  echo "KC_FINANCE_BACKEND_SECRET=$$(openssl rand -hex 32)" >> deploy/.env; \
	  echo "Đã sinh secret cho client finance-backend vào deploy/.env"; }
	$(COMPOSE) up -d --build --wait postgres redis rabbitmq keycloak mailpit
	$(COMPOSE) run --rm keycloak-config

down: ## Tắt hạ tầng nhưng GIỮ nguyên dữ liệu trong volume
	$(COMPOSE) down

reset: ## Tắt hạ tầng và XOÁ sạch dữ liệu — database trở về trắng
	$(COMPOSE) down -v

ps: ## Xem các container đang sống của projecy này và healthy hay không
	$(COMPOSE) ps

logs: ## Xem log realtime của hạ tầng (Ctrl+C để thoát)
	$(COMPOSE) logs -f

# ---------- Chạy ứng dụng ----------

run: ## Chạy Spring Boot ở cổng 18473 — chiếm terminal, Ctrl+C để dừng
	KEYCLOAK_ADMIN_CLIENT_SECRET="$${KEYCLOAK_ADMIN_CLIENT_SECRET:-$$(sed -n 's/^KC_FINANCE_BACKEND_SECRET=//p' deploy/.env 2>/dev/null | tail -1)}" \
	  $(GRADLE) bootRun

health: ## Gọi /actuator/health để biết backend đã sống chưa
	@curl -fsS $(BASE_URL)/actuator/health && echo

swagger: ## Mở Swagger UI trên trình duyệt
	@open $(BASE_URL)/swagger-ui.html

rabbit: ## Mở trang quản trị RabbitMQ (tài khoản finance / finance)
	@open http://localhost:15672

# ---------- Keycloak ----------

kc: ## Mở Keycloak Admin Console (tài khoản admin / admin)
	@open http://localhost:8082/admin

mail: ## Mở Mailpit để đọc mail verify / reset password Keycloak gửi
	@open http://localhost:8025

kc-db: ## Tạo database `keycloak` thủ công — chỉ cần khi volume postgres đã có sẵn từ trước
	@$(COMPOSE) exec -T postgres psql -U finance -d finance -v ON_ERROR_STOP=0 \
	  -c "CREATE USER keycloak WITH PASSWORD 'keycloak';" \
	  -c "CREATE DATABASE keycloak OWNER keycloak;" || true
	@echo "Xong. Nếu báo 'already exists' thì database đã có sẵn, không sao."

# ---------- Public HTTPS (Cloudflare Quick Tunnel) ----------

tunnel: ## Bật HTTPS công khai *.trycloudflare.com (Caddy + cloudflared trong docker); URL đổi mỗi lần bật lại
	@deploy/tunnel.sh up

tunnel-off: ## Tắt tunnel, Keycloak quay về http://10.0.2.2:8082
	@deploy/tunnel.sh down

tunnel-url: ## In URL tunnel đang dùng
	@deploy/tunnel.sh url

# ---------- Production (chạy TRÊN SERVER, xem deploy/PROD.md) ----------
# Server không có mã nguồn: CI chép Makefile + deploy/ theo tag và chạy deploy/release.sh <tag>.
# deploy/.release (IMAGE_TAG=<tag đang chạy>) do release.sh ghi — có thì mọi lệnh prod-* dùng đúng bản đó.

PROD_ENV     ?= deploy/.env.prod
COMPOSE_PROD := docker compose -f deploy/docker-compose.prod.yml --env-file $(PROD_ENV) \
                $(addprefix --env-file ,$(wildcard deploy/.release))
BACKUP_DIR   ?= $(HOME)/finance-backups

prod-secrets: ## [server] Tạo deploy/.env.prod từ mẫu, sinh ngẫu nhiên mọi mật khẩu (không ghi đè file đã có)
	@test ! -f $(PROD_ENV) || { echo "$(PROD_ENV) đã tồn tại — không ghi đè."; exit 1; }
	@sed -e "s/^DB_PASSWORD=.*/DB_PASSWORD=$$(openssl rand -hex 24)/" \
	     -e "s/^KC_DB_PASSWORD=.*/KC_DB_PASSWORD=$$(openssl rand -hex 24)/" \
	     -e "s/^RABBITMQ_PASSWORD=.*/RABBITMQ_PASSWORD=$$(openssl rand -hex 24)/" \
	     -e "s/^KC_ADMIN_PASSWORD=.*/KC_ADMIN_PASSWORD=$$(openssl rand -hex 24)/" \
	     -e "s/^KC_FINANCE_BACKEND_SECRET=.*/KC_FINANCE_BACKEND_SECRET=$$(openssl rand -hex 32)/" \
	     deploy/.env.prod.example > $(PROD_ENV)
	@chmod 600 $(PROD_ENV)
	@echo "Đã tạo $(PROD_ENV). Còn phải điền tay: DOMAIN, SMTP_USER, SMTP_PASSWORD, SMTP_FROM."

prod-up: ## [Mac] Thử stack production tại chỗ: build image từ mã nguồn (IMAGE_TAG=local), chờ healthy, đồng bộ realm
	IMAGE_TAG=local $(COMPOSE_PROD) up -d --build --wait
	IMAGE_TAG=local $(COMPOSE_PROD) run --rm keycloak-config

prod-down: ## [server] Tắt stack production, GIỮ dữ liệu
	$(COMPOSE_PROD) down

prod-ps: ## [server] Trạng thái các container production
	$(COMPOSE_PROD) ps

prod-logs: ## [server] Log realtime (lọc một service: make prod-logs S=app)
	$(COMPOSE_PROD) logs -f --tail=200 $(S)

prod-health: ## [server] Gọi health qua HTTPS công khai
	@curl -fsS https://$$(sed -n 's/^DOMAIN=//p' $(PROD_ENV))/actuator/health && echo

prod-backup: ## [server] pg_dumpall (cả DB finance lẫn keycloak) vào $(BACKUP_DIR), giữ 7 bản mới nhất
	@mkdir -p $(BACKUP_DIR)
	@# sh của make không có pipefail ⇒ kiểm dòng kết của pg_dumpall để biết dump trọn vẹn, hỏng thì xoá + báo lỗi.
	@f=$(BACKUP_DIR)/finance-$$(date +%Y%m%d-%H%M%S).sql.gz; \
	  $(COMPOSE_PROD) exec -T postgres pg_dumpall -U finance | gzip > $$f; \
	  gunzip -c $$f | tail -n 5 | grep -q 'dump complete' || { rm -f $$f; echo "Backup HỎNG: pg_dumpall lỗi"; exit 1; }; \
	  echo "Backup: $$f"
	@ls -1t $(BACKUP_DIR)/finance-*.sql.gz | tail -n +8 | xargs -r rm -f
	@ls -lh $(BACKUP_DIR) | tail -n 8

# ---------- Production: chạy từ Mac, điều khiển server qua SSH ----------
# SRV là alias trong ~/.ssh/config (HostName, User, IdentityFile). Deploy thường ngày: đẩy tag v* — CI lo hết.
# Các lệnh dưới để dựng server lần đầu, deploy lại / lùi bản, xem log.

SRV        ?= finance-prod
SRV_DIR    := finance-backend
CI_KEY     ?= $(HOME)/.ssh/finance_ci_deploy
# Giống bước rsync trong .github/workflows/deploy.yml: không bao giờ đụng secret và tag đang chạy trên server.
RSYNC_SKIP := --exclude .env.prod --exclude .release --exclude .env --exclude .tunnel --exclude .DS_Store

srv-setup: ## [Mac→server] Lần đầu: cài Docker, mở 80/443 trong máy, múi giờ, cron backup
	scp -q deploy/server-bootstrap.sh $(SRV):/tmp/server-bootstrap.sh
	ssh $(SRV) 'sudo bash /tmp/server-bootstrap.sh && rm /tmp/server-bootstrap.sh'

srv-sync: ## [Mac→server] Lần đầu: chép Makefile + deploy/ (cấu hình, không có mã nguồn) để chạy srv-env
	rsync -az --delete $(RSYNC_SKIP) Makefile deploy $(SRV):$(SRV_DIR)/

srv-env: ## [Mac→server] Tạo (nếu chưa có) rồi mở deploy/.env.prod trên server để điền SMTP — make srv-env DOMAIN=xxx.duckdns.org
	@test -n "$(DOMAIN)" || { echo "Thiếu DOMAIN=xxx.duckdns.org"; exit 1; }
	ssh -t $(SRV) 'cd $(SRV_DIR) && { test -f deploy/.env.prod || make -s prod-secrets; } \
	  && sed -i "s/^DOMAIN=$$/DOMAIN=$(DOMAIN)/" deploy/.env.prod && nano deploy/.env.prod'

srv-smtp-key: ## [Mac→server] Dán SMTP key Brevo (không hiện trên màn hình) vào deploy/.env.prod trên server
	@printf 'Dán SMTP key Brevo rồi Enter (không hiện ra): '; stty -echo; read -r key; stty echo; echo; \
	  test -n "$$key" || { echo "Key rỗng — không đổi gì."; exit 1; }; \
	  printf '%s' "$$key" | ssh $(SRV) 'cd $(SRV_DIR) && k=$$(cat) && sed -i "s|^SMTP_PASSWORD=.*|SMTP_PASSWORD=$$k|" deploy/.env.prod \
	    && grep -q "^SMTP_PASSWORD=." deploy/.env.prod && echo "✅ Đã lưu SMTP key vào deploy/.env.prod trên server."'

srv-ci: ## [Mac→server+GitHub] Một lần: tạo SSH key riêng cho CI, cài lên server, nạp 4 secret DEPLOY_* (cần gh auth login)
	@test -f $(CI_KEY) || ssh-keygen -q -t ed25519 -N '' -C finance-ci-deploy -f $(CI_KEY)
	@host=$$(ssh -G $(SRV) | awk '$$1=="hostname"{print $$2}'); \
	  user=$$(ssh -G $(SRV) | awk '$$1=="user"{print $$2}'); \
	  known=$$(ssh-keygen -F "$$host" | grep -v '^#'); \
	  test -n "$$known" || { echo "~/.ssh/known_hosts chưa có $$host — chạy 'ssh $(SRV)' một lần trước"; exit 1; }; \
	  pub=$$(cut -d' ' -f1,2 $(CI_KEY).pub); \
	  ssh $(SRV) "grep -qF '$$pub' ~/.ssh/authorized_keys || echo 'restrict $$pub finance-ci-deploy' >> ~/.ssh/authorized_keys" && \
	  gh secret set DEPLOY_SSH_KEY < $(CI_KEY) && \
	  gh secret set DEPLOY_HOST --body "$$host" && \
	  gh secret set DEPLOY_USER --body "$$user" && \
	  printf '%s\n' "$$known" | gh secret set DEPLOY_KNOWN_HOSTS && \
	  echo "✅ Key CI đã cài lên server (quyền 'restrict': chỉ chạy lệnh) và 4 secret DEPLOY_* đã nạp vào repo."

srv-deploy: ## [Mac→GitHub] Deploy lại / lùi về một tag đã có image — make srv-deploy TAG=v2.1.0
	@test -n "$(TAG)" || { echo "Thiếu TAG=vX.Y.Z"; exit 1; }
	gh workflow run deploy.yml -f tag=$(TAG)
	@sleep 5
	gh run watch --exit-status $$(gh run list --workflow deploy.yml --limit 1 --json databaseId --jq '.[0].databaseId')

srv-logs: ## [Mac→server] Log production (lọc: make srv-logs S=app)
	ssh -t $(SRV) 'cd $(SRV_DIR) && make prod-logs S=$(S)'

srv-admin: ## [Mac→server] Mở tunnel tới Keycloak Admin Console → http://localhost:8082 (Ctrl-C để đóng)
	@echo "Admin console: http://localhost:8082/admin  (user admin, mật khẩu KC_ADMIN_PASSWORD trong .env.prod trên server)"
	ssh -N -L 8082:127.0.0.1:8082 $(SRV)

# ---------- Kiểm thử & chất lượng ----------

test: ## Chạy unit test + ArchUnit + Modulith verify (không cần Docker)
	$(GRADLE) test

itest: ## Chạy integration test bằng Testcontainers (bắt buộc có Docker)
	$(GRADLE) integrationTest

build: ## Build đầy đủ: test + kiểm tra format + đóng gói boot jar
	$(GRADLE) build

fmt: ## Tự format lại code theo google-java-format
	$(GRADLE) spotlessApply

lint: ## Chỉ kiểm tra format, không sửa file (giống CI)
	$(GRADLE) spotlessCheck

api: ## Sinh lại interface + DTO từ api/openapi.yaml
	$(GRADLE) openApiGenerate

clean: ## Xoá thư mục build/
	$(GRADLE) clean
