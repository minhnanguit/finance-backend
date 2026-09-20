# Mỗi target làm đúng MỘT việc. Muốn chạy nối tiếp thì liệt kê nhiều target:
#   make up run          (bật hạ tầng rồi chạy app)
# Gõ `make` hoặc `make help` để xem danh sách.

COMPOSE  := docker compose -f deploy/docker-compose.yml
GRADLE   := ./gradlew
BASE_URL ?= http://localhost:8080

.DEFAULT_GOAL := help
.PHONY: help up down reset ps logs run health swagger rabbit test itest build fmt lint api clean

help: ## Liệt kê mọi lệnh make của repo này
	@echo "finance-backend — các lệnh có sẵn:"
	@grep -E '^[a-z][a-zA-Z_-]*:.*## ' $(MAKEFILE_LIST) \
	  | awk 'BEGIN{FS=":.*## "}{printf "  \033[36m%-9s\033[0m %s\n", $$1, $$2}'

# ---------- Hạ tầng (Docker) ----------

up: ## Bật Postgres + Redis + RabbitMQ chạy nền, chờ tới khi cả 3 healthy
	$(COMPOSE) up -d --wait

down: ## Tắt hạ tầng nhưng GIỮ nguyên dữ liệu trong volume
	$(COMPOSE) down

reset: ## Tắt hạ tầng và XOÁ sạch dữ liệu — database trở về trắng
	$(COMPOSE) down -v

ps: ## Xem 3 container đang sống và healthy hay không
	$(COMPOSE) ps

logs: ## Xem log realtime của hạ tầng (Ctrl+C để thoát)
	$(COMPOSE) logs -f

# ---------- Chạy ứng dụng ----------

run: ## Chạy Spring Boot ở cổng 8080 — chiếm terminal, Ctrl+C để dừng
	$(GRADLE) bootRun

health: ## Gọi /actuator/health để biết backend đã sống chưa
	@curl -fsS $(BASE_URL)/actuator/health && echo

swagger: ## Mở Swagger UI trên trình duyệt
	@open $(BASE_URL)/swagger-ui.html

rabbit: ## Mở trang quản trị RabbitMQ (tài khoản finance / finance)
	@open http://localhost:15672

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
