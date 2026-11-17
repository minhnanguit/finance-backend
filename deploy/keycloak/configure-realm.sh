#!/usr/bin/env bash
set -euo pipefail

KCADM=/opt/keycloak/bin/kcadm.sh

"$KCADM" config credentials \
  --server "${KEYCLOAK_URL}" \
  --realm master \
  --user "${KEYCLOAK_ADMIN}" \
  --password "${KEYCLOAK_ADMIN_PASSWORD}"

"$KCADM" get realms/finance >/dev/null
"$KCADM" update realms/finance \
  -s 'loginTheme=finance' \
  -s 'emailTheme=finance' \
  -s 'internationalizationEnabled=true' \
  -s 'supportedLocales=["vi","en"]' \
  -s 'defaultLocale=vi'

# Client finance-backend (service account xoá tài khoản Keycloak, ADR-008 A8). --import-realm chỉ chạy cho realm
# mới, nên realm có từ trước được bổ sung ở đây: chạy lại bao nhiêu lần cũng ra cùng một kết quả.
if [[ -z "${KC_FINANCE_BACKEND_SECRET:-}" ]]; then
  echo "KC_FINANCE_BACKEND_SECRET is empty: run 'make up' so it is generated into deploy/.env" >&2
  exit 1
fi
backend_client_id() {
  "$KCADM" get clients -r finance -q clientId=finance-backend --fields id --format csv --noquotes
}
CLIENT_ID="$(backend_client_id)"
if [[ -z "$CLIENT_ID" ]]; then
  "$KCADM" create clients -r finance \
    -s clientId=finance-backend \
    -s 'name=Finance Backend (service account)' \
    -s enabled=true \
    -s protocol=openid-connect \
    -s publicClient=false \
    -s clientAuthenticatorType=client-secret \
    -s standardFlowEnabled=false \
    -s implicitFlowEnabled=false \
    -s directAccessGrantsEnabled=false \
    -s serviceAccountsEnabled=true \
    -s 'attributes."access.token.lifespan"=300'
  CLIENT_ID="$(backend_client_id)"
  echo "Created client finance-backend."
fi
# Secret luôn khớp deploy/.env, cái `make run` truyền cho backend.
"$KCADM" update "clients/${CLIENT_ID}" -r finance -s "secret=${KC_FINANCE_BACKEND_SECRET}"
# Quyền tối thiểu: chỉ manage-users (ASVS V1.4).
"$KCADM" add-roles -r finance \
  --uusername service-account-finance-backend \
  --cclientid realm-management \
  --rolename manage-users

# SMTP thật (production, deploy/docker-compose.prod.yml). Dev không truyền SMTP_HOST nên giữ Mailpit của realm-finance.json.
if [[ -n "${SMTP_HOST:-}" ]]; then
  "$KCADM" update realms/finance \
    -s "smtpServer.host=${SMTP_HOST}" \
    -s "smtpServer.port=${SMTP_PORT:-587}" \
    -s 'smtpServer.starttls=true' \
    -s 'smtpServer.ssl=false' \
    -s 'smtpServer.auth=true' \
    -s "smtpServer.user=${SMTP_USER}" \
    -s "smtpServer.password=${SMTP_PASSWORD}" \
    -s "smtpServer.from=${SMTP_FROM}" \
    -s "smtpServer.fromDisplayName=${SMTP_FROM_NAME:-Finance}"
  echo "SMTP set to ${SMTP_HOST}:${SMTP_PORT:-587}."
fi

echo "Finance realm theme, locales and the finance-backend client are configured."
