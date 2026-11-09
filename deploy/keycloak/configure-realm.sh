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

echo "Finance realm theme and locales are configured."
