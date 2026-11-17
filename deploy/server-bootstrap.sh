#!/usr/bin/env bash
# Chuẩn bị server Ubuntu 24.04 mới, chạy MỘT lần bằng sudo (Mac: `make srv-setup`).
# Chạy lại an toàn: bước nào đã làm thì bỏ qua.
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo "Chạy bằng sudo"; exit 1; }
OWNER=${SUDO_USER:-ubuntu}
HOME_DIR=$(getent passwd "$OWNER" | cut -d: -f6)

echo "== Gói cơ bản"
export DEBIAN_FRONTEND=noninteractive
apt-get -o DPkg::Lock::Timeout=300 update -qq  # máy mới: unattended-upgrades có thể đang giữ khoá apt
apt-get -o DPkg::Lock::Timeout=300 install -y -qq make rsync curl ca-certificates >/dev/null

echo "== Docker"
command -v docker >/dev/null || curl -fsSL https://get.docker.com | sh
usermod -aG docker "$OWNER"
systemctl enable --now docker >/dev/null

echo "== Firewall trong máy: mở 80/tcp, 443/tcp, 443/udp"
# Image Ubuntu của Oracle có luật REJECT cuối chain INPUT — chèn ACCEPT trước nó. Máy không có luật đó thì bỏ qua.
reject=$(iptables -L INPUT --line-numbers -n | awk '$2=="REJECT"{print $1; exit}')
if [[ -n "$reject" ]]; then
  for rule in "-p tcp --dport 80" "-p tcp --dport 443" "-p udp --dport 443"; do
    # shellcheck disable=SC2086
    iptables -C INPUT $rule -m state --state NEW -j ACCEPT 2>/dev/null \
      || iptables -I INPUT "$reject" $rule -m state --state NEW -j ACCEPT
  done
  command -v netfilter-persistent >/dev/null && netfilter-persistent save >/dev/null
fi

echo "== Múi giờ"
timedatectl set-timezone Asia/Ho_Chi_Minh

echo "== Cron backup 23:30 hằng ngày (máy Azure tự tắt 00:00–07:00)"
line='30 23 * * * cd ~/finance-backend && make prod-backup >> ~/finance-backups/backup.log 2>&1'
current=$(crontab -u "$OWNER" -l 2>/dev/null | grep -vF 'make prod-backup' || true)
printf '%s\n%s\n' "$current" "$line" | sed '/^$/d' | crontab -u "$OWNER" -
install -d -o "$OWNER" -g "$OWNER" "$HOME_DIR/finance-backend" "$HOME_DIR/finance-backups"

echo "✅ Xong. Docker $(docker --version | awk '{print $3}' | tr -d ,)"
