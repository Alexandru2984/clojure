#!/usr/bin/env bash
set -euo pipefail

cd /home/micu/clojure

if [[ ! -f .env ]]; then
  echo ".env not found" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

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

echo "Checking event listing"
curl -fsS "$base/api/events?source=deploy-check&limit=5"
echo

echo "Checking stats"
curl -fsS "$base/api/events/stats"
echo
