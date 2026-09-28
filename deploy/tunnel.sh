#!/usr/bin/env bash
# Bật / tắt public HTTPS cho Keycloak + API qua Cloudflare Quick Tunnel (*.trycloudflare.com).
#   deploy/tunnel.sh up      bật tunnel, ghi KC_PUBLIC_URL vào deploy/.env, recreate Keycloak
#   deploy/tunnel.sh down    tắt tunnel, Keycloak quay về http://10.0.2.2:8081
#   deploy/tunnel.sh url     in URL đang dùng
#
# Hai chế độ (TUNNEL_MODE):
#   docker  service `edge` + `tunnel` trong docker-compose (profile "tunnel"). MẶC ĐỊNH.
#   native  cloudflared + caddy chạy thẳng trên máy (brew install cloudflared caddy) — đường vòng khi
#           Docker không pull được image: TUNNEL_MODE=native make tunnel
set -euo pipefail

cd "$(dirname "$0")"
COMPOSE=(docker compose -f docker-compose.yml)
ENV_FILE=.env
STATE_DIR=.tunnel            # pid + log của chế độ native (git-ignored)
EDGE_PORT=${EDGE_PORT:-8088} # cổng Caddy khi chạy native

TUNNEL_MODE=${TUNNEL_MODE:-docker}
case $TUNNEL_MODE in docker|native) ;; *) echo "TUNNEL_MODE phải là docker hoặc native" >&2; exit 2 ;; esac

current_url() { [[ -f $ENV_FILE ]] && sed -n 's/^KC_PUBLIC_URL=//p' "$ENV_FILE" | tail -1 || true; }

write_url() { # $1 = URL, rỗng thì xoá
  touch "$ENV_FILE"
  grep -v '^KC_PUBLIC_URL=' "$ENV_FILE" > "$ENV_FILE.tmp" || true
  [[ -n $1 ]] && echo "KC_PUBLIC_URL=$1" >> "$ENV_FILE.tmp"
  mv "$ENV_FILE.tmp" "$ENV_FILE"
}

recreate_keycloak() {
  echo "⏳ recreate Keycloak với hostname mới…"
  "${COMPOSE[@]}" up -d --wait keycloak >/dev/null 2>&1
}

# ---------- native ----------
native_running() { [[ -f $STATE_DIR/$1.pid ]] && kill -0 "$(cat "$STATE_DIR/$1.pid")" 2>/dev/null; }

native_start() {
  mkdir -p "$STATE_DIR"
  if ! native_running edge; then
    EDGE_LISTEN=":$EDGE_PORT" KEYCLOAK_UPSTREAM=localhost:8081 BACKEND_UPSTREAM="${BACKEND_UPSTREAM:-localhost:8080}" \
      nohup caddy run --adapter caddyfile --config edge/Caddyfile > "$STATE_DIR/edge.log" 2>&1 &
    echo $! > "$STATE_DIR/edge.pid"
  fi
  if ! native_running tunnel; then
    : > "$STATE_DIR/tunnel.log"
    nohup cloudflared tunnel --no-autoupdate --url "http://localhost:$EDGE_PORT" > "$STATE_DIR/tunnel.log" 2>&1 &
    echo $! > "$STATE_DIR/tunnel.pid"
  fi
}

native_stop() {
  for p in tunnel edge; do
    if native_running $p; then kill "$(cat "$STATE_DIR/$p.pid")" 2>/dev/null || true; fi
    rm -f "$STATE_DIR/$p.pid"
  done
}

native_logs() { cat "$STATE_DIR/tunnel.log" 2>/dev/null || true; }

# ---------- docker ----------
docker_start() { "${COMPOSE[@]}" --profile tunnel up -d edge tunnel >/dev/null 2>&1; }
docker_stop()  { "${COMPOSE[@]}" --profile tunnel rm -sf edge tunnel >/dev/null 2>&1 || true; }
docker_logs()  { "${COMPOSE[@]}" logs tunnel 2>/dev/null || true; }

case "${1:-}" in
  up)
    echo "▶ chế độ: $TUNNEL_MODE"
    "${TUNNEL_MODE}_start"
    echo "⏳ chờ Cloudflare cấp URL…"
    url=""
    for _ in $(seq 1 60); do
      url=$("${TUNNEL_MODE}_logs" | grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' | tail -1 || true)
      [[ -n $url ]] && break
      sleep 2
    done
    if [[ -z $url ]]; then
      echo "❌ không lấy được URL (chế độ $TUNNEL_MODE)."
      [[ $TUNNEL_MODE == native ]] && tail -5 "$STATE_DIR/tunnel.log"
      exit 1
    fi

    if [[ $url != "$(current_url)" ]]; then
      write_url "$url"
      recreate_keycloak
    fi
    cat <<MSG
✅ Public URL: $url
   Keycloak issuer : $url/realms/finance
   API             : $url/api/v1/...
   Admin Console   : KHÔNG public — vẫn chỉ ở http://localhost:8081/admin

Bước tiếp theo:
  1. Backend : Ctrl+C rồi 'make run' lại (Makefile tự lấy issuer mới từ deploy/.env)
  2. Mobile  : cd ../finance-mobile && make tunnel URL=$url && make install

⚠️  URL đổi mỗi khi tunnel khởi động lại — khi đó chạy lại 'make tunnel' rồi làm lại 2 bước trên.
MSG
    ;;
  down)
    native_stop
    command -v docker >/dev/null && docker_stop
    write_url ""
    recreate_keycloak
    echo "✅ Đã tắt tunnel. Keycloak quay về http://10.0.2.2:8081 — restart backend và chạy 'make tunnel-off' bên mobile."
    ;;
  url)
    u=$(current_url); echo "${u:-(chưa bật tunnel — đang dùng http://10.0.2.2:8081)}"
    ;;
  *)
    echo "Dùng: $0 up|down|url" >&2; exit 2 ;;
esac
