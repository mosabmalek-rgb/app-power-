#!/usr/bin/env bash
set -euo pipefail

# Usage:
# POWERHUB_REMOTE_CONFIG_PRIVATE_KEY=/secure/path/remote-config-ed25519-private.pem \
#   ./scripts/sign-remote-config.sh 1 https://temporary-powerhub-host.example/ > powerhub-mobile-config.json

: "${POWERHUB_REMOTE_CONFIG_PRIVATE_KEY:?حدد مسار المفتاح الخاص عبر POWERHUB_REMOTE_CONFIG_PRIVATE_KEY}"

CONFIG_VERSION="${1:?مرر رقم إصدار التهيئة أولاً}"
API_BASE_URL="${2:?مرر عنوان المنصة HTTPS ثانياً}"

if [[ ! "$API_BASE_URL" =~ ^https://[^/?#]+/$ ]]; then
  echo "يجب أن يكون عنوان المنصة جذراً آمناً وينتهي بشرطة مائلة، مثل https://powerhub.example.com/" >&2
  exit 1
fi

PAYLOAD="${CONFIG_VERSION}"$'\n'"${API_BASE_URL}"
PAYLOAD_FILE="$(mktemp)"
trap 'rm -f "$PAYLOAD_FILE"' EXIT
printf '%s' "$PAYLOAD" > "$PAYLOAD_FILE"
SIGNATURE="$(openssl pkeyutl -sign -rawin -inkey "$POWERHUB_REMOTE_CONFIG_PRIVATE_KEY" -in "$PAYLOAD_FILE" | base64 -w0)"

cat <<JSON
{
  "config_version": ${CONFIG_VERSION},
  "api_base_url": "${API_BASE_URL}",
  "signature": "${SIGNATURE}"
}
JSON
