# Clojure EventPulse

Clojure EventPulse, also named Clojure Event Stream Inspector, is a small production web dashboard for receiving, validating, storing, filtering, and visualizing JSON events from VPS projects.

Public URL: https://clojure.micutu.com

## Features

- Modern dark dashboard with live event stream.
- Summary cards for totals, recent windows, warnings, errors, sources, and last event time.
- Recent events table with source, level, type, message, timestamp, and pretty-printed metadata.
- Filters by source, level, type, and limit.
- JSON ingestion API protected by `X-API-Key`.
- Admin login for viewing real event data.
- Public visitors see demo/mock dashboard data only.
- Server-Sent Events live updates through `/api/events/stream`.
- SQLite event history with cleanup that keeps the latest 5,000 events.
- `/docs` page with API usage and schema reference.
- `/health` endpoint for monitoring.

## Stack

- Clojure 1.11 on Java 21
- Leiningen
- Ring + Jetty
- Cheshire for JSON
- next.jdbc + SQLite JDBC
- Plain JavaScript with Server-Sent Events
- Nginx reverse proxy + Certbot SSL
- systemd service

## Run On The VPS

From `/home/micu/clojure`:

```bash
lein test
lein uberjar
java -jar target/eventpulse-standalone.jar
```

The app reads configuration from `.env` and binds only to `127.0.0.1`.

## Environment Variables

Stored in `.env`, which is ignored by Git:

```bash
APP_HOST=127.0.0.1
APP_PORT=8120
APP_API_KEY=generated-secret-value
ADMIN_USERNAME=admin
ADMIN_PASSWORD=generated-admin-password
ADMIN_SESSION_SECRET=generated-session-secret
DB_PATH=data/eventpulse.sqlite3
```

Do not commit or publish `.env`.

## Admin Login

Open `https://clojure.micutu.com/login` and sign in with the admin credentials from `.env`.

Unauthenticated visitors can still open the dashboard, but the dashboard, stats API, event listing API, and SSE endpoint return mock data instead of real VPS event data.

## API Key Usage

Use a placeholder in documentation and scripts you share publicly:

```bash
curl -X POST https://clojure.micutu.com/api/events \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: REPLACE_WITH_API_KEY' \
  -d '{"source":"weather-sim","level":"warning","type":"high_cpu","message":"Simulation tick delayed","metadata":{"cpu":91,"service":"simulation"}}'
```

## Event Schema

Required fields:

- `source`: non-empty string, max 80 characters
- `level`: one of `debug`, `info`, `warning`, `error`, `critical`
- `type`: non-empty string, max 80 characters
- `message`: non-empty string, max 1000 characters

Optional fields:

- `metadata`: JSON object, max 8192 bytes when serialized

Unknown fields are rejected. Request bodies larger than 32768 bytes are rejected.

## API Endpoints

- `GET /health`
- `GET /api/events`
- `GET /api/events?source=weather-sim&level=warning&type=high_cpu&limit=50`
- `GET /api/events/stats`
- `GET /api/events/stream`
- `POST /api/events`
- `GET /login`
- `POST /login`
- `GET /logout`
- `GET /docs`

## Deployment

Systemd service name:

```bash
clojure-eventpulse.service
```

Nginx config path:

```bash
/etc/nginx/sites-available/clojure.micutu.com
```

Enabled site path:

```bash
/etc/nginx/sites-enabled/clojure.micutu.com
```

The systemd service runs as user `micu`, uses `/home/micu/clojure` as `WorkingDirectory`, reads `/home/micu/clojure/.env`, and restarts automatically on failure.

## Smoke Checks

```bash
scripts/deploy_check.sh
curl -fsS http://127.0.0.1:8120/health
curl -fsS https://clojure.micutu.com/health
systemctl status clojure-eventpulse.service --no-pager
journalctl -u clojure-eventpulse.service -n 100 --no-pager
nginx -t
```

## Security Notes

- The application binds to `127.0.0.1`, never `0.0.0.0`.
- Nginx is the public TLS reverse proxy.
- `POST /api/events` requires `X-API-Key`.
- Real dashboard data requires admin login.
- Public unauthenticated dashboard traffic receives mock data only.
- API key is stored only in `.env`.
- The frontend and README do not expose the real API key.
- Incoming JSON is strictly validated.
- Client IP is stored only as a short SHA-256 hash prefix.
- Nginx adds security headers.
- The application exposes no shell execution, code execution, or user-controlled file paths.

## Limitations And TODOs

- v1 uses an in-process SSE fanout, which is suitable for lightweight monitoring on this VPS.
- Rate limiting is handled at the Nginx layer if enabled in the site config; deeper per-key application rate limiting can be added later.
- There is no login UI; dashboard visibility is controlled by the public site and event ingestion is API-key protected.
- SQLite is intentionally used for simplicity. Move to PostgreSQL if event volume grows substantially.

## Git Note

Git commits and pushes are manual. This deployment was prepared without running `git add`, `git commit`, or `git push`.
