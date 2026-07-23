# Fight the Fascists

Protest mutual-aid coordination platform — a mobile-first, anonymous-by-default PWA for coordinating supply needs, volunteer shifts, and verified announcements at protest sites.

## Documentation

- [PRD.md](./PRD.md) — product requirements and feature specifications
- [ARCHITECTURE.md](./ARCHITECTURE.md) — technical stack, data model, and implementation guide

## Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 19 + Vite + TypeScript + Tailwind PWA |
| Backend | Spring Boot 3.3 (WebFlux) + Java 21 |
| Database | PostgreSQL 16 (Flyway migrations) |
| Cache | Redis 7 (rate limits, idempotency) |
| Realtime | Server-Sent Events |

## Quick start

### Prerequisites

- Java 21, Maven 3.8+
- Node.js 22+
- PostgreSQL 16 and Redis 7 (or use Docker Compose)

### With Docker Compose

```bash
docker compose up --build
```

API: http://localhost:8080  
Frontend dev server: `cd frontend && npm run dev` → http://localhost:5173

### Local development

```bash
# Start Postgres + Redis, then:
createdb fightthefascists  # user: ftf / password: ftf

# Backend (runs Flyway migrations automatically)
cd backend && mvn spring-boot:run

# Frontend (proxies /api to :8080)
cd frontend && npm install && npm run dev
```

### Run tests

```bash
make test
```

## P0 features implemented

- **Device identity** — pseudonymous handles, HMAC-SHA256 device hash, forget-device
- **Zones** — 8 pre-seeded zones with Hindi/English names
- **Need board (F1)** — post, list, dedup cluster cap, content filter, encrypted notes, community "already covered"
- **Claims (F2)** — partial claims, handoff codes, race-safe pledging, auto-lapse
- **Abuse layer** — proof-of-work, rate limiting, idempotency keys
- **SSE** — realtime board updates
- **Lite mode** — zero-JS fallback at `/api/v1/chapters/{slug}/lite`
- **i18n** — Hindi + English UI toggle
- **PWA** — offline shell caching, installable

## P1 features implemented

- **Volunteer shifts (F4)** — 3-hour blocks, signup caps, understaffed highlighting, night-watch notice
- **Announcements (F7)** — steward-only logistics feed with source labels, urgent 2-confirm flow, retractions
- **First aid directory (F5)** — aid points with stale-status expiry, non-dismissable emergency banner
- **Stewards (F6)** — passphrase + TOTP login, JWT (10 min), grant/revoke stewards, panic revoke-all
- **Moderation queue** — review flagged needs, approve or remove

### Dev steward login

Default admin credentials (change in production):
- Passphrase: `steward-dev-pass`
- TOTP secret: `JBSWY3DPEHPK3PXP` (add to any authenticator app)

## Multi-chapter architecture

Each protest site is a **chapter** — an independent instance of the platform sharing the same deployment. Delhi Jantar Mantar 2026 is the first chapter (`delhi-2026`); future sites (e.g. `berlin-2027`) can be added without code changes.

- **Home** (`/`) — chapter picker listing active and planned chapters
- **Chapter app** (`/{chapterSlug}/…`) — e.g. `/delhi-2026` for the need board
- **Global APIs** — device registration, PoW, steward login, chapter list
- **Chapter-scoped APIs** — needs, zones, shifts, announcements, etc. under `/api/v1/chapters/{chapterSlug}/…`
- **Admin-only writes** — `POST/PATCH /api/v1/admin/chapters` to create, update, activate, or archive chapters

### Create a new chapter (admin)

```bash
curl -X POST http://localhost:8080/api/v1/admin/chapters \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "slug": "berlin-2027",
    "nameEn": "Berlin 2027",
    "locationLabelEn": "Brandenburg Gate, Berlin",
    "siteLat": 52.516, "siteLon": 13.378,
    "timezone": "Europe/Berlin",
    "headcountEstimate": 500,
    "publicUrl": "https://fight-the-fascists.com/berlin-2027"
  }'

# Then activate when ready:
curl -X POST http://localhost:8080/api/v1/admin/chapters/berlin-2027/activate \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

## P2 features implemented

- **Bulk pledges (F8)** — org/recurring supply lane with food safety ack, steward approval, missed-streak downgrade
- **Demand forecasting (F11)** — projected shortfalls next 6h based on headcount, heat band, bulk supply
- **Heat band (F12)** — open-meteo temperature → GREEN/AMBER/RED site-wide advisory banner
- **Printable board** — A4 PDF with needs list + QR code at `/api/v1/chapters/{slug}/board.pdf`
- **Static mirror** — per-chapter JSON+HTML snapshot regenerated every 60s at `/api/v1/chapters/{slug}/mirror`
- **Transparency page** — aggregate 24h stats at `/api/v1/chapters/{slug}/stats` and `/{slug}/about` in the app

## API overview

Base URL: `/api/v1`

### Global

| Method | Path | Description |
|--------|------|-------------|
| GET | `/chapters` | List active/planned chapters |
| GET | `/chapters/{slug}` | Chapter details |
| POST | `/devices/register` | Register pseudonymous device |
| GET | `/pow/challenge` | Get proof-of-work challenge |
| POST | `/admin/login` | Steward/admin login (passphrase + TOTP) |
| POST | `/admin/chapters` | Create chapter (admin only) |
| PATCH | `/admin/chapters/{slug}` | Update chapter (admin only) |
| POST | `/admin/chapters/{slug}/activate` | Activate chapter (admin only) |
| POST | `/admin/chapters/{slug}/archive` | Archive chapter (admin only) |

### Per chapter (`/chapters/{chapterSlug}/…`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/zones` | List site zones |
| GET | `/needs` | List open needs |
| POST | `/needs` | Post a need (requires X-PoW, Idempotency-Key) |
| POST | `/needs/{id}/claims` | Claim a need |
| POST | `/claims/deliver` | Confirm delivery via handoff code |
| GET | `/stream` | SSE event stream (chapter-scoped) |
| GET | `/lite` | Zero-JS HTML board |
| GET | `/shifts` | Volunteer shift roster |
| POST | `/shifts/{id}/signup` | Sign up for a shift |
| GET | `/announcements` | Published announcements |
| POST | `/announcements` | Post announcement (steward+) |
| GET | `/aid-points` | First aid directory |
| GET | `/moderation/queue` | Review queue (steward+) |
| GET/POST | `/bulk-pledges` | Bulk org supply pledges |
| GET | `/forecast` | Projected shortfalls next 6h |
| GET | `/heat-band` | Heat advisory band |
| GET | `/stats` | Transparency aggregate counters |
| GET | `/board.pdf` | Printable A4 board with QR |
| GET | `/mirror` | Static JSON snapshot |
| GET | `/mirror/html` | Static HTML mirror |

## Project structure

```
backend/          Spring Boot API (com.fightthefascists)
frontend/         React PWA
docker-compose.yml
PRD.md
ARCHITECTURE.md
```

## Security

All responses include security headers (CSP, HSTS, Permissions-Policy). No geolocation, no PII collection, no third-party trackers. See PRD §8 for the full privacy specification.
