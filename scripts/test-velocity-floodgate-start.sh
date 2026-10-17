#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "${tmp}"' EXIT
mkdir -p "${tmp}/bin" "${tmp}/app/plugins/floodgate" "${tmp}/config"
sed "s|/app|${tmp}/app|g" "${repo_root}/velocity/scripts/start.sh" >"${tmp}/start.sh"
cat >"${tmp}/bin/java" <<'EOF'
#!/bin/sh
exit 0
EOF
chmod +x "${tmp}/bin/java"

fail() { echo "Velocity Floodgate startup contract failed: $1" >&2; exit 1; }

printf 'shared-key-bytes\000\377' >"${tmp}/key.pem"
printf 'username-prefix: "."\nplayer-link:\n  enabled: false\n' >"${tmp}/config/config.yml"
chmod 0444 "${tmp}/config/config.yml"

VELOCITY_HOME="${tmp}/app" FLOODGATE_KEY_FILE="${tmp}/key.pem" \
  FLOODGATE_CONFIG_FILE="${tmp}/config/config.yml" PATH="${tmp}/bin:${PATH}" \
  sh "${tmp}/start.sh" >/dev/null
cmp "${tmp}/key.pem" "${tmp}/app/plugins/floodgate/key.pem" || fail "key bytes changed"
cmp "${tmp}/config/config.yml" "${tmp}/app/plugins/floodgate/config.yml" || fail "managed config was not copied byte-for-byte"

VELOCITY_HOME="${tmp}/app" FLOODGATE_KEY_FILE="${tmp}/key.pem" \
  FLOODGATE_CONFIG_FILE="${tmp}/config/config.yml" PATH="${tmp}/bin:${PATH}" \
  sh "${tmp}/start.sh" >/dev/null
cmp "${tmp}/config/config.yml" "${tmp}/app/plugins/floodgate/config.yml" || fail "repeat startup did not restore the managed config"
mode="$(stat -c '%a' "${tmp}/app/plugins/floodgate/config.yml")"
case "$mode" in
  *[2367][0-7][0-7]) ;;
  *) fail "copied runtime config is not owner-writable (mode ${mode})" ;;
esac

if VELOCITY_HOME="${tmp}/app" FLOODGATE_CONFIG_FILE="${tmp}/missing.yml" \
  PATH="${tmp}/bin:${PATH}" sh "${tmp}/start.sh" >/dev/null 2>&1; then
  fail "missing explicit config was silently ignored"
fi

echo "Velocity Floodgate startup contract passed"
