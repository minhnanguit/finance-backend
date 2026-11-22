#!/usr/bin/env bash
# Lưu SMTP key Brevo vào deploy/.env.prod — CHỈ sau khi đăng nhập SMTP thử thành công — rồi đồng bộ realm Keycloak.
# Chạy trên server, key đọc từ stdin (không nằm trên dòng lệnh / lịch sử shell). Từ Mac: make srv-smtp-key
set -euo pipefail
cd "$(dirname "$0")/.."
ENV_FILE=${PROD_ENV:-deploy/.env.prod}
[[ -f $ENV_FILE ]] || { echo "❌ Chưa có $ENV_FILE"; exit 1; }

IFS= read -r key || true
key=${key%$'\r'}
key=${key#"${key%%[![:space:]]*}"}; key=${key%"${key##*[![:space:]]}"}   # bỏ khoảng trắng đầu/cuối khi dán
[[ -n $key ]] || { echo "❌ Key rỗng — không đổi gì."; exit 1; }
[[ $key != *[[:space:]]* ]] || { echo "❌ Key có khoảng trắng — có vẻ đã dán nhầm thứ khác (câu lệnh?). Không đổi gì."; exit 1; }
[[ $key != xkeysib-* ]] || { echo "❌ Đây là API key (xkeysib-…). Cần SMTP key: Brevo → SMTP & API → tab SMTP → Generate a new SMTP key."; exit 1; }

get() { sed -n "s/^$1=//p" "$ENV_FILE" | tail -1; }
echo "== Thử đăng nhập $(get SMTP_HOST):$(get SMTP_PORT) bằng $(get SMTP_USER)"
SMTP_HOST=$(get SMTP_HOST) SMTP_PORT=$(get SMTP_PORT) SMTP_USER=$(get SMTP_USER) SMTP_KEY=$key python3 - <<'PY'
import os, smtplib, sys
try:
    s = smtplib.SMTP(os.environ["SMTP_HOST"], int(os.environ["SMTP_PORT"] or 587), timeout=20)
    s.starttls()
    s.login(os.environ["SMTP_USER"], os.environ["SMTP_KEY"])
    s.quit()
except smtplib.SMTPAuthenticationError as e:
    sys.exit(f"❌ Brevo từ chối ({e.smtp_code}): key sai/đã bị tắt, hoặc SMTP_USER không khớp login SMTP. Không đổi gì.")
except Exception as e:
    sys.exit(f"❌ Không kết nối được SMTP: {e}. Không đổi gì.")
PY
echo "✅ Đăng nhập SMTP thành công"

tmp=$(mktemp "$ENV_FILE.XXXX")
KEY=$key awk 'BEGIN{k=ENVIRON["KEY"]} /^SMTP_PASSWORD=/{print "SMTP_PASSWORD=" k; next} {print}' "$ENV_FILE" > "$tmp"
chmod 600 "$tmp" && mv "$tmp" "$ENV_FILE"
echo "✅ Đã lưu vào $ENV_FILE"

if [[ -f deploy/.release ]]; then
  echo "== Áp SMTP mới vào realm Keycloak"
  make -s prod-realm PROD_ENV="$ENV_FILE"
fi
