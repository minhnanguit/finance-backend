#!/usr/bin/env bash
# Deploy một phiên bản image đã có trên GHCR. Chạy TRÊN SERVER, từ thư mục finance-backend:
#   deploy/release.sh v2.1.0
# CI gọi script này qua SSH (.github/workflows/deploy.yml), sau khi đã `docker login ghcr.io`.
# Tag đang chạy ghi ở deploy/.release. Hỏng sau khi đã đổi bản ⇒ tự quay về bản trước, thoát mã 1.
# Chạy lại cùng tag là an toàn. Env tuỳ chọn: PROD_ENV (deploy/.env.prod), WAIT_TIMEOUT (600 giây).
set -euo pipefail
cd "$(dirname "$0")/.."

TAG=${1:?Thiếu tag — ví dụ: deploy/release.sh v2.1.0}
[[ $TAG =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$ ]] || { echo "Tag không hợp lệ: $TAG"; exit 2; }
ENV_FILE=${PROD_ENV:-deploy/.env.prod}
WAIT_TIMEOUT=${WAIT_TIMEOUT:-600}
RELEASE_FILE=deploy/.release
[[ -f $ENV_FILE ]] || { echo "Chưa có $ENV_FILE — tạo bằng 'make srv-env' (từ Mac) hoặc 'make prod-secrets'."; exit 2; }

# IMAGE_TAG truyền qua env của tiến trình (ưu tiên hơn --env-file) để chọn đúng bản cho từng lệnh.
dc() { docker compose -f deploy/docker-compose.prod.yml --env-file "$ENV_FILE" "$@"; }
step() { printf '\n== %s\n' "$*"; }

prev=$(sed -n 's/^IMAGE_TAG=//p' "$RELEASE_FILE" 2>/dev/null || true)
step "Deploy ${prev:-<chưa có bản nào>} → $TAG"

# 1. Backup trước khi đổi bản: Flyway migrate lúc app khởi động, rollback image không rollback schema.
if [[ -n $(dc ps --status running -q postgres 2>/dev/null) ]]; then
  step "Backup database"
  make -s prod-backup PROD_ENV="$ENV_FILE"
fi

# 2. Kéo image. Tag phiên bản không bao giờ bị ghi đè ⇒ có sẵn trên máy thì không kéo lại (rollback nhanh).
step "Kéo image $TAG"
IMAGE_TAG=$TAG dc pull --quiet --policy missing app keycloak \
  || { echo "Không kéo được image $TAG (chưa build xong? chưa docker login ghcr.io?) — chưa đổi gì."; exit 1; }
images=()  # bash 3.2 (Mac) không có mapfile
while IFS= read -r img; do images+=("$img"); done < <(IMAGE_TAG=$TAG dc config --images | grep -F ":$TAG" | sort -u)
[[ ${#images[@]} -gt 0 ]] || { echo "Compose không có image nào theo tag — chưa đổi gì."; exit 1; }
for img in "${images[@]}"; do
  docker image inspect "$img" >/dev/null 2>&1 || { echo "Không có image $img — chưa đổi gì."; exit 1; }
done

# 3. Bật bản mới. Hỏng từ đây trở đi ⇒ rollback.
start() {  # $1 = tag
  echo "IMAGE_TAG=$1" > "$RELEASE_FILE"
  IMAGE_TAG=$1 dc up -d --no-build --pull missing --wait --wait-timeout "$WAIT_TIMEOUT" \
    && IMAGE_TAG=$1 dc run --rm --no-deps keycloak-config
}
healthy() {
  local domain insecure=--fail  # thử trên Mac (DOMAIN=localhost): Caddy dùng CA nội bộ ⇒ bỏ kiểm chứng chỉ
  domain=$(sed -n 's/^DOMAIN=//p' "$ENV_FILE")
  [[ $domain == localhost ]] && insecure=--insecure
  curl -fsS "$insecure" --retry 10 --retry-delay 3 --retry-all-errors "https://$domain/actuator/health" \
    | grep -q '"status":"UP"'
}
diagnose() {
  dc ps -a || true
  dc logs --no-color --tail 40 app keycloak caddy || true
}

step "Bật $TAG (chờ healthy, tối đa ${WAIT_TIMEOUT}s) + đồng bộ realm Keycloak"
if start "$TAG" && { step "Kiểm tra health qua HTTPS"; healthy; }; then
  step "Dọn image cũ (giữ $TAG${prev:+ và $prev})"
  for img in "${images[@]}"; do
    repo=${img%:*}
    docker image ls "$repo" --format '{{.Tag}}' | grep -vxF -e "$TAG" -e "${prev:-$TAG}" -e local \
      | sed "s|^|$repo:|" | xargs -r docker image rm >/dev/null || true
  done
  docker image prune -f >/dev/null
  step "✅ Đang chạy $TAG"
  exit 0
fi

echo "!! $TAG hỏng."
diagnose
if [[ -z $prev || $prev == "$TAG" ]]; then
  echo "!! Không có bản trước để quay về — cần xử lý tay (make prod-logs S=app)."
  exit 1
fi
step "Rollback về $prev"
if start "$prev" && healthy; then
  echo "↩️  Đã quay về $prev. Deploy $TAG THẤT BẠI."
else
  echo "!!! Rollback về $prev cũng hỏng — cần xử lý tay. Backup mới nhất: ~/finance-backups"
  diagnose
fi
exit 1
