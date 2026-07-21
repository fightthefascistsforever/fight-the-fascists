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
- **Lite mode** — zero-JS fallback at `/api/v1/lite`
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

## API overview

Base URL: `/api/v1`

| Method | Path | Description |
|--------|------|-------------|
| POST | `/devices/register` | Register pseudonymous device |
| GET | `/zones` | List site zones |
| GET | `/needs` | List open needs |
| POST | `/needs` | Post a need (requires X-PoW, Idempotency-Key) |
| POST | `/needs/{id}/claims` | Claim a need |
| POST | `/claims/deliver` | Confirm delivery via handoff code |
| GET | `/pow/challenge` | Get proof-of-work challenge |
| GET | `/stream` | SSE event stream |
| GET | `/lite` | Zero-JS HTML board |
| GET | `/shifts` | Volunteer shift roster |
| POST | `/shifts/{id}/signup` | Sign up for a shift |
| GET | `/announcements` | Published announcements |
| POST | `/announcements` | Post announcement (steward+) |
| GET | `/aid-points` | First aid directory |
| POST | `/admin/login` | Steward/admin login (passphrase + TOTP) |
| GET | `/moderation/queue` | Review queue (steward+) |

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
