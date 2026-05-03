#!/usr/bin/env bash
set -euo pipefail

cd /home/micu/clojure

password="${1:-}"
if [[ -z "$password" ]]; then
  read -r -s -p "New admin password: " password
  echo
fi

if [[ ${#password} -lt 20 ]]; then
  echo "Admin password must be at least 20 characters." >&2
  exit 1
fi

hash="$(printf '%s' "$password" | lein run -m eventpulse.auth)"
tmp="$(mktemp)"
grep -v '^ADMIN_PASSWORD=' .env | grep -v '^ADMIN_PASSWORD_HASH=' | grep -v '^ADMIN_SESSION_SECRET=' > "$tmp"
printf 'ADMIN_PASSWORD_HASH=%s\n' "$hash" >> "$tmp"
install -m 600 "$tmp" .env
rm -f "$tmp"

echo "Admin password hash updated in .env"
