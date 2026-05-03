#!/usr/bin/env bash
set -euo pipefail

cd /home/micu/clojure

if [[ ! -f .env ]]; then
  echo ".env not found" >&2
  exit 1
fi

env_value() {
  local key="$1"
  sed -n "s/^${key}=//p" .env | tail -n 1
}

APP_HOST="$(env_value APP_HOST)"
APP_PORT="$(env_value APP_PORT)"
APP_API_KEY="$(env_value APP_API_KEY)"
ADMIN_USERNAME="$(env_value ADMIN_USERNAME)"

if [[ -z "${ADMIN_PASSWORD:-}" && -f .admin-login ]]; then
  # shellcheck disable=SC1091
  source .admin-login
fi

base="http://${APP_HOST:-127.0.0.1}:${APP_PORT}"

echo "Checking $base/health"
curl -fsS "$base/health"
echo

echo "Checking API key rejection"
code="$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$base/api/events" \
  -H 'Content-Type: application/json' \
  -d '{"source":"deploy-check","level":"info","type":"unauthorized","message":"should fail"}')"
test "$code" = "401"
echo "Unauthorized POST returned $code"

echo "Checking successful event insertion"
curl -fsS -X POST "$base/api/events" \
  -H 'Content-Type: application/json' \
  -H "X-API-Key: $APP_API_KEY" \
  -d '{"source":"deploy-check","level":"info","type":"smoke","message":"Deployment smoke check","metadata":{"service":"clojure-eventpulse"}}'
echo

echo "Checking public mock event listing"
curl -fsS "$base/api/events?limit=2"
echo

echo "Checking admin login and real event listing"
if [[ -z "${ADMIN_PASSWORD:-}" ]]; then
  echo "ADMIN_PASSWORD not available for deploy check; skipping admin login check" >&2
  exit 1
fi
jar="$(mktemp)"
trap 'rm -f "$jar"' EXIT
login_code="$(curl -sS -o /dev/null -w '%{http_code}' -c "$jar" -X POST "$base/login" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "username=$ADMIN_USERNAME" \
  --data-urlencode "password=$ADMIN_PASSWORD")"
test "$login_code" = "303"
curl -fsS -b "$jar" "$base/api/events?source=deploy-check&limit=5"
echo

echo "Checking stats"
curl -fsS -b "$jar" "$base/api/events/stats"
echo
