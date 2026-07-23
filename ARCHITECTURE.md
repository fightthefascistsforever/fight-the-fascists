# ARCHITECTURE — Fight the Fascists

> Companion to `PRD.md`. Read the PRD first. Feature IDs (`F1`, `F2.E5`) refer to it.
> Target implementer: Cursor AI + one senior backend engineer (Java/Spring Boot background).

---

## 1. Stack decision

| Layer | Choice | Why |
|---|---|---|
| Frontend | **React 18 + Vite + TypeScript**, PWA via Workbox | Small bundle, no SSR framework overhead, easy offline story |
| Styling | **Tailwind** (purged) | No runtime CSS-in-JS, tiny output |
| State | **TanStack Query** + Zustand for local UI | Cache, retry, offline-friendly |
| Local persistence | **IndexedDB** via `idb` | Offline write queue |
| Backend | **Spring Boot 3.3 (Java 21), WebFlux** | Plays to your existing strength; WebFlux for SSE fan-out at low thread cost |
| DB | **PostgreSQL 16** | Row-level constraints, partial indexes, `pg_cron` for TTL purges |
| Cache / limits / realtime bus | **Redis 7** | Token buckets, idempotency keys, pub/sub for SSE fan-out across pods |
| Realtime | **SSE** (not WebSocket) | One-way is all we need; survives proxies; auto-reconnect built in; cheaper |
| Edge | **Cloudflare** (proxy, WAF, rate limit, caching) | Free DDoS absorption; configure minimal logging |
| Deploy | 2 small containers + managed Postgres + managed Redis | K8s is overkill at this scale; keep ops surface tiny |
| Static mirror | Cloudflare Pages / S3, regenerated every 60s | Survives origin outage |

**Explicitly NOT used:** Google Maps/Analytics/Fonts, Firebase, any SaaS with default PII capture, WebSockets, native apps, any auth provider.

---

## 2. System diagram

```
                    ┌──────────────────────────────┐
                    │   Cloudflare (WAF, DDoS,     │
                    │   caching, bot score)        │
                    └──────────────┬───────────────┘
                                   │
        ┌──────────────────────────┼──────────────────────────┐
        │                          │                          │
┌───────▼────────┐        ┌────────▼─────────┐      ┌─────────▼──────────┐
│ Static mirror  │        │  API (Spring)    │      │  /lite  (SSR HTML) │
│ (read-only     │        │  x2 replicas     │      │  served by API     │
│  JSON+HTML)    │        │                  │      │  zero JS           │
└────────────────┘        └───┬──────────┬───┘      └────────────────────┘
      ▲                       │          │
      │ regen 60s             │          │
      └───────────────────────┤          │
                              │          │
                    ┌─────────▼──┐  ┌────▼──────┐
                    │ PostgreSQL │  │  Redis    │
                    │ (encrypted)│  │ buckets,  │
                    │ pg_cron    │  │ pubsub,   │
                    │ purge jobs │  │ idem keys │
                    └────────────┘  └───────────┘

Client (PWA)
  ├── Service worker: app shell cache, offline read, background sync queue
  ├── IndexedDB: queued writes with idempotency keys
  └── SSE connection: /api/v1/stream?zones=...
```

---

## 3. Data model (PostgreSQL DDL)

```sql
-- ============ ENUMS ============
CREATE TYPE need_state    AS ENUM ('OPEN','CLAIMED','FULFILLED','EXPIRED','CANCELLED','WITHDRAWN');
CREATE TYPE need_category AS ENUM ('WATER','FOOD_COOKED','FOOD_DRY','ORS_ELECTROLYTE','MEDICAL_SUPPLY',
                                   'SHADE_TARPAULIN','BEDDING','SANITATION','POWER_CHARGING','CLOTHING','OTHER');
CREATE TYPE qty_unit      AS ENUM ('LITRES','MEALS','PACKETS','PIECES','PEOPLE_SERVED');
CREATE TYPE urgency_level AS ENUM ('ROUTINE','SOON','URGENT');
CREATE TYPE claim_state   AS ENUM ('ACTIVE','DELIVERED','LAPSED','CANCELLED');
CREATE TYPE trust_tier    AS ENUM ('ANON','TRUSTED','ZONE_STEWARD','ADMIN');
CREATE TYPE zone_status   AS ENUM ('ACTIVE','INACCESSIBLE','ARCHIVED');

-- ============ ZONES ============
CREATE TABLE zones (
  id              SMALLSERIAL PRIMARY KEY,
  code            TEXT NOT NULL UNIQUE,          -- 'A','B','GATE2'
  name_en         TEXT NOT NULL,
  name_hi         TEXT NOT NULL,
  landmark_en     TEXT NOT NULL,                 -- 'blue tarp, north side'
  landmark_hi     TEXT NOT NULL,
  handoff_point   TEXT NOT NULL,
  svg_x           SMALLINT NOT NULL,             -- position on the site diagram
  svg_y           SMALLINT NOT NULL,
  status          zone_status NOT NULL DEFAULT 'ACTIVE',
  fallback_zone_id SMALLINT REFERENCES zones(id),-- F3.E2
  sort_order      SMALLINT NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============ DEVICES (pseudonymous) ============
CREATE TABLE devices (
  device_hash       BYTEA PRIMARY KEY,           -- HMAC(device_secret, pepper). NEVER the raw secret.
  handle            TEXT NOT NULL,               -- 'Teal Ibex 41' — generated, non-unique-ish, no PII
  tier              trust_tier NOT NULL DEFAULT 'ANON',
  steward_zone_id   SMALLINT REFERENCES zones(id),
  reliability_score SMALLINT NOT NULL DEFAULT 50 CHECK (reliability_score BETWEEN 0 AND 100),
  deliveries_ok     SMALLINT NOT NULL DEFAULT 0,
  deliveries_lapsed SMALLINT NOT NULL DEFAULT 0,
  first_seen_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked           BOOLEAN NOT NULL DEFAULT false,
  pepper_version    SMALLINT NOT NULL            -- so pepper rotation is traceable (§8.2 PRD)
);
CREATE INDEX ON devices (last_seen_at);   -- for the purge job

-- ============ NEEDS ============
CREATE TABLE needs (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  zone_id          SMALLINT NOT NULL REFERENCES zones(id),
  category         need_category NOT NULL,
  quantity         NUMERIC(10,2) NOT NULL CHECK (quantity > 0),
  unit             qty_unit NOT NULL,
  pledged          NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (pledged >= 0),
  delivered        NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (delivered >= 0),
  urgency          urgency_level NOT NULL,
  note_enc         BYTEA,                        -- app-layer encrypted, max 200 chars plaintext
  state            need_state NOT NULL DEFAULT 'OPEN',
  created_by       BYTEA NOT NULL REFERENCES devices(device_hash) ON DELETE CASCADE,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  needed_by        TIMESTAMPTZ NOT NULL,
  expires_at       TIMESTAMPTZ NOT NULL,
  resolved_at      TIMESTAMPTZ,
  resolution_reason TEXT,
  covered_flags    SMALLINT NOT NULL DEFAULT 0,  -- F1.E5
  hidden_pending_review BOOLEAN NOT NULL DEFAULT false,
  version          INTEGER NOT NULL DEFAULT 0    -- optimistic locking
);
-- Partial index: the hot query is "open needs, by zone, soonest first"
CREATE INDEX needs_open_idx ON needs (zone_id, needed_by)
  WHERE state IN ('OPEN','CLAIMED') AND hidden_pending_review = false;
CREATE INDEX needs_expiry_idx ON needs (expires_at) WHERE state IN ('OPEN','CLAIMED');
-- F1.E2: enforce the dedup cluster cap with a trigger (see §4.2)

-- ============ CLAIMS ============
CREATE TABLE claims (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  need_id       UUID NOT NULL REFERENCES needs(id) ON DELETE CASCADE,
  device_hash   BYTEA NOT NULL REFERENCES devices(device_hash) ON DELETE CASCADE,
  quantity      NUMERIC(10,2) NOT NULL CHECK (quantity > 0),
  delivered_qty NUMERIC(10,2),
  eta           TIMESTAMPTZ NOT NULL,
  lapse_at      TIMESTAMPTZ NOT NULL,            -- eta + 60min
  handoff_code  TEXT NOT NULL,
  state         claim_state NOT NULL DEFAULT 'ACTIVE',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at   TIMESTAMPTZ
);
CREATE INDEX ON claims (need_id) WHERE state = 'ACTIVE';
CREATE INDEX ON claims (lapse_at) WHERE state = 'ACTIVE';
CREATE UNIQUE INDEX claims_active_code_idx ON claims (handoff_code) WHERE state = 'ACTIVE'; -- F2.E9
-- F2.E8 cap enforced in service layer + this:
CREATE INDEX claims_device_active_idx ON claims (device_hash) WHERE state = 'ACTIVE';

-- ============ SHIFTS ============
CREATE TABLE shifts (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  zone_id       SMALLINT NOT NULL REFERENCES zones(id),
  role          TEXT NOT NULL,
  starts_at     TIMESTAMPTZ NOT NULL,
  ends_at       TIMESTAMPTZ NOT NULL,
  min_volunteers SMALLINT NOT NULL DEFAULT 2,
  max_volunteers SMALLINT NOT NULL DEFAULT 6,
  handover_note_enc BYTEA,
  CHECK (ends_at > starts_at)
);
CREATE UNIQUE INDEX ON shifts (zone_id, role, starts_at);

CREATE TABLE shift_signups (
  shift_id     UUID NOT NULL REFERENCES shifts(id) ON DELETE CASCADE,
  device_hash  BYTEA NOT NULL REFERENCES devices(device_hash) ON DELETE CASCADE,
  confirmed    BOOLEAN NOT NULL DEFAULT false,   -- F4.E4 steward confirm for night roles
  checked_in_at TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (shift_id, device_hash)
);

-- ============ ANNOUNCEMENTS ============
CREATE TABLE announcements (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  body_en       TEXT NOT NULL,
  body_hi       TEXT,
  source        TEXT NOT NULL CHECK (source IN ('OBSERVED_ON_SITE','ORGANISER_CONFIRMED','OFFICIAL_NOTICE')),
  urgent        BOOLEAN NOT NULL DEFAULT false,
  confirmations SMALLINT NOT NULL DEFAULT 1,     -- F7.E4: urgent needs 2
  published     BOOLEAN NOT NULL DEFAULT false,
  retracted_by  UUID REFERENCES announcements(id),
  author_hash   BYTEA NOT NULL REFERENCES devices(device_hash),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  editable_until TIMESTAMPTZ NOT NULL,
  expires_at    TIMESTAMPTZ NOT NULL
);

-- ============ BULK PLEDGES ============
CREATE TABLE bulk_pledges (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_name      TEXT NOT NULL,
  contact_note  TEXT,                            -- org's own choice, optional
  category      need_category NOT NULL,
  quantity      NUMERIC(10,2) NOT NULL,
  unit          qty_unit NOT NULL,
  rrule         TEXT NOT NULL,                   -- e.g. 'FREQ=DAILY;BYHOUR=20'
  approved_by   BYTEA REFERENCES devices(device_hash),  -- F8.E3
  active        BOOLEAN NOT NULL DEFAULT true,
  missed_streak SMALLINT NOT NULL DEFAULT 0,     -- F8.E1
  prep_window_minutes SMALLINT,                  -- F8.E2 cooked food
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============ FLAGS / MODERATION ============
CREATE TABLE flags (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  target_type  TEXT NOT NULL,                    -- 'NEED','ANNOUNCEMENT','LOSTFOUND'
  target_id    UUID NOT NULL,
  device_hash  BYTEA NOT NULL,
  reason       TEXT NOT NULL CHECK (reason IN ('ALREADY_COVERED','NOT_REAL','DUPLICATE','ABUSIVE')),
  weight       NUMERIC(3,1) NOT NULL DEFAULT 1.0,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ON flags (target_type, target_id, device_hash);  -- one flag per device per target

-- ============ AUDIT (content-free) ============
CREATE TABLE mod_audit (
  id          BIGSERIAL PRIMARY KEY,
  actor_hash  BYTEA NOT NULL,
  action      TEXT NOT NULL,
  target_id   UUID,
  at          TIMESTAMPTZ NOT NULL DEFAULT now()
  -- deliberately NO content column
);

-- ============ FIRST AID ============
CREATE TABLE aid_points (
  id          SMALLSERIAL PRIMARY KEY,
  zone_id     SMALLINT NOT NULL REFERENCES zones(id),
  name        TEXT NOT NULL,
  status      TEXT NOT NULL DEFAULT 'UNKNOWN',
  status_at   TIMESTAMPTZ NOT NULL DEFAULT now(),  -- F5.E2 stale after 4h
  hours_note  TEXT,
  cannot_handle TEXT
);

-- ============ LOST & FOUND (objects only) ============
CREATE TABLE lost_found (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  zone_id     SMALLINT NOT NULL REFERENCES zones(id),
  kind        TEXT NOT NULL CHECK (kind IN ('FOUND','LOST')),
  object_desc_enc BYTEA NOT NULL,
  device_hash BYTEA NOT NULL REFERENCES devices(device_hash) ON DELETE CASCADE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at  TIMESTAMPTZ NOT NULL              -- F10.E3 always +72h
);
```

### 3.1 Retention jobs (`pg_cron`, run every 5 min)
```sql
SELECT cron.schedule('purge', '*/5 * * * *', $$
  DELETE FROM needs WHERE state IN ('FULFILLED','EXPIRED','CANCELLED','WITHDRAWN')
        AND resolved_at < now() - interval '24 hours';
  DELETE FROM needs WHERE state IN ('OPEN','CLAIMED') AND expires_at < now() - interval '24 hours';
  DELETE FROM claims WHERE state <> 'ACTIVE' AND resolved_at < now() - interval '24 hours';
  DELETE FROM shift_signups s USING shifts sh
        WHERE s.shift_id = sh.id AND sh.ends_at < now() - interval '24 hours';
  DELETE FROM announcements WHERE expires_at < now();
  DELETE FROM lost_found WHERE expires_at < now();
  DELETE FROM flags WHERE created_at < now() - interval '48 hours';
  DELETE FROM devices WHERE last_seen_at < now() - interval '30 days';
$$);
```
> **Do not rely on the app alone for deletion.** The DB-level job is the guarantee. Also verify backup retention ≤ 24h or the policy is theatre.

---

## 4. Backend design

### 4.1 Module layout
```
com.fightthefascists
├── config/          SecurityConfig, RedisConfig, CorsConfig, HeadersConfig
├── identity/        DeviceService, PepperRotationService, HandleGenerator
├── needs/           NeedController, NeedService, NeedRepository, DedupChecker
├── claims/          ClaimController, ClaimService, HandoffCodeService, LapseScheduler
├── zones/           ZoneController, ZoneService
├── shifts/          ShiftController, ShiftService
├── announce/        AnnouncementController, AnnouncementService (2-of-N confirm)
├── bulk/            BulkPledgeController, DemandOffsetService
├── moderation/      FlagService, ContentFilter, ReviewQueueController
├── abuse/           PowChallengeService, RateLimiter, CircuitBreaker, ReliabilityScorer
├── realtime/        SseHub, RedisFanout
├── forecast/        DemandForecastService, HeatBandService
├── lite/            LiteController  (server-rendered, zero-JS)
└── ops/             KillSwitchController, MirrorSnapshotJob, PurgeVerifier
```

### 4.2 Dedup cluster cap (F1.E2) — DB trigger, not just app logic
```sql
CREATE OR REPLACE FUNCTION check_need_cluster() RETURNS TRIGGER AS $$
DECLARE c INT;
BEGIN
  SELECT count(*) INTO c FROM needs
   WHERE zone_id = NEW.zone_id AND category = NEW.category AND state = 'OPEN';
  IF c >= 3 THEN
    RAISE EXCEPTION 'DUPLICATE_NEED_CLUSTER' USING ERRCODE = 'P0001';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_need_cluster BEFORE INSERT ON needs
  FOR EACH ROW EXECUTE FUNCTION check_need_cluster();
```
Map `P0001 / DUPLICATE_NEED_CLUSTER` → HTTP 409 with the conflicting need IDs in the body.

### 4.3 Race-safe claiming (F2.E5)
```sql
UPDATE needs
   SET pledged = pledged + :qty,
       state   = CASE WHEN pledged + :qty >= quantity THEN 'CLAIMED' ELSE state END,
       version = version + 1
 WHERE id = :needId
   AND state IN ('OPEN','CLAIMED')
   AND hidden_pending_review = false
   AND pledged + :qty <= quantity * 1.5;   -- F1.E3 over-fulfilment cap
```
`rowsAffected == 0` → return `409 NEED_ALREADY_COVERED` **with an `alternatives[]` array of 3 nearby under-served needs**. Never return a bare error; always give the user a next action.

### 4.4 Idempotency (F1.E8/E9)
- All mutating endpoints require header `Idempotency-Key: <uuid>`.
- Redis: `SET idem:{deviceHash}:{key} <responseJson> EX 86400 NX`. If the key exists, return the stored response with `Idempotency-Replayed: true`. If the key exists but the value is a sentinel `IN_PROGRESS`, return `409 REQUEST_IN_FLIGHT` (client retries with backoff).

### 4.5 Rate limiting
Redis token buckets, evaluated in this order; first failure wins:
```
device:{hash}:needs      → 5 / hour
device:{hash}:claims     → 10 / hour, max 3 ACTIVE concurrently
device:{hash}:flags      → 10 / hour
device:{hash}:any_write  → 30 / hour
zone:{id}:needs          → 20 / hour
ipprefix:{/24}:writes    → 60 / hour     (prefix only — never store the full IP)
global:writes            → 600 / min     (breaches raise PoW difficulty, do not block)
```
Return `429` with `Retry-After` and a human message. For a device that's plausibly a busy real coordinator (`TRUSTED`+), route excess to the review queue instead of rejecting.

### 4.6 Proof of work
- `GET /api/v1/pow/challenge` → `{ challenge, difficulty, expiresAt }`. Challenge = random 16 bytes, HMAC'd server-side so no server state is needed to verify.
- Client finds `nonce` such that `sha256(challenge + nonce)` has `difficulty` leading zero bits. Difficulty 16 ≈ 100–300 ms on a mid-range Android.
- Submit with the write in header `X-PoW: challenge.nonce`. Server verifies + burns the challenge (Redis `SETNX`, TTL 5 min) so it can't be replayed.
- `CircuitBreaker` raises difficulty (16 → 20 → 22) when global write rate or new-device rate spikes. Announce nothing about this to the client except a slightly longer spinner.
- Run PoW in a **Web Worker** so the UI never freezes.

### 4.7 Content filter pipeline (F1.E12, F5.E1, F10.E1)
Runs in this order on every free-text field:
1. **Normalise** — Unicode NFKC, strip zero-width chars and homoglyph substitutions (attackers use `рhone` with a Cyrillic р).
2. **Hard strip** — regex-remove URLs, `\+?91[-\s]?\d{10}`, generic 10+ digit runs, emails, `\w+@\w+` UPI patterns. Replace with `[removed]`.
3. **Emergency intercept** — medical keyword list (EN + HI + Hinglish transliteration) → return `EMERGENCY_INTERCEPT` so the client shows the modal *before* submitting. Persist nothing.
4. **Blocklist** — slurs, weapons, alcohol, drug names, person-description patterns → `hidden_pending_review = true`, queued for Steward. User sees "sent for a quick check."
5. **Shape checks** — length ≤200, ≤3 consecutive identical chars, ≤10% emoji, at least 2 word chars.
6. **Encrypt** — AES-256-GCM with a key from the secret manager, store as `note_enc`.

> Keep the wordlists in `resources/filters/*.txt`, loaded at boot and hot-reloadable. **Do not hardcode them in Java source** — they need updating without a redeploy.

### 4.8 Reliability scoring
```
on DELIVERED:  score = min(100, score + 8);  deliveries_ok++
on LAPSED:     score = max(0,  score - 12);  deliveries_lapsed++
on CANCELLED (>2h before ETA):  no change     // cancelling early is good behaviour
daily decay:   score += (50 - score) * 0.1    // drifts back to neutral
```
Capability gates (never shown to the user as a "score"):
- `score < 25` → max 1 active claim, max claim qty 20% of need, flag weight 0.25
- `score 25–60` → max 2 active claims, flag weight 0.5 (ANON) / 1.0 (TRUSTED)
- `score > 60` and `deliveries_ok >= 3` across ≥2 distinct zones confirmed by ≥3 distinct devices → eligible for `TRUSTED`

### 4.9 Scheduled jobs
| Job | Cadence | Purpose |
|---|---|---|
| `ExpireNeedsJob` | 60s | `OPEN/CLAIMED` past `expires_at` → `EXPIRED`, broadcast |
| `LapseClaimsJob` | 60s | `ACTIVE` past `lapse_at` → `LAPSED`, return qty to pool, re-broadcast if urgent |
| `StaleAidStatusJob` | 15m | aid points >4h old → `UNKNOWN` |
| `BulkMissedStreakJob` | hourly | F8.E1 downgrade phantom recurring pledges |
| `MirrorSnapshotJob` | 60s | write static JSON+HTML board to object storage |
| `PepperRotationJob` | weekly | rotate HMAC pepper, expire the mapping table after 7d |
| `PurgeVerifierJob` | daily | assert no row exists older than its policy; alert if it does |
| `ForecastJob` | 15m | recompute projected demand per category/zone |

All jobs use a Redis lock (`SET job:{name} EX 300 NX`) so two replicas don't double-run.

---

## 5. API specification

Base: `/api/v1`. All responses include `serverNow` (ISO-8601 UTC) for F1.E7.

### Conventions
- Auth: header `X-Device: <base64 device_secret>` → server computes hash. Steward/Admin additionally send `Authorization: Bearer <jwt>` (≤10 min TTL).
- Errors: `{ "error": { "code": "NEED_ALREADY_COVERED", "message": "...", "messageHi": "...", "alternatives": [...] } }`
- All list endpoints paginate: `?cursor=&limit=` (max 100).

| Method | Path | Notes |
|---|---|---|
| `POST` | `/devices/register` | Returns `{handle, tier, serverNow}`. Idempotent on the hash. |
| `DELETE` | `/devices/me` | §8.2 — purge everything for this hash. Cascades. |
| `GET` | `/zones` | Cacheable 5 min. Includes SVG layout coords. |
| `GET` | `/needs?zone=&category=&state=open&cursor=` | ≤15 KB for 100 items |
| `POST` | `/needs` | PoW + idempotency required |
| `PATCH` | `/needs/{id}` | Creator or Steward. `If-Match: <version>` |
| `POST` | `/needs/{id}/cancel` | |
| `POST` | `/needs/{id}/flag` | body `{reason}` |
| `POST` | `/needs/{id}/claims` | body `{quantity, etaMinutes}` → returns `handoffCode` |
| `POST` | `/claims/{id}/cancel` | |
| `POST` | `/claims/deliver` | body `{handoffCode, deliveredQty}` — any on-site device may call (F2.E3) |
| `GET` | `/shifts?from=&to=` | |
| `POST` | `/shifts/{id}/signup` | |
| `DELETE`| `/shifts/{id}/signup` | |
| `PUT` | `/shifts/{id}/handover` | outgoing volunteer's note |
| `GET` | `/announcements` | |
| `POST` | `/announcements` | Steward+. Urgent requires 2nd confirm (F7.E4) |
| `POST` | `/announcements/{id}/confirm` | 2nd steward |
| `POST` | `/announcements/{id}/retract` | posts a CORRECTION |
| `GET` | `/aid-points` | |
| `PATCH`| `/aid-points/{id}` | Steward+ |
| `GET` | `/bulk-pledges` / `POST` | Steward approval to count toward demand |
| `GET` | `/forecast` | projected shortfalls next 6h |
| `GET` | `/pow/challenge` | |
| `GET` | `/stream?zones=A,B` | **SSE** |
| `GET` | `/lite` | server-rendered HTML, zero JS |
| `GET` | `/board.pdf` | printable A4 board with QR |
| `GET` | `/stats` | aggregate transparency counters |
| `POST` | `/admin/killswitch` | `{mode: READ_ONLY \| STATIC \| NORMAL}`, 2-of-N |
| `POST` | `/admin/revoke-all-stewards` | panic action (F6.E1) |

### SSE event types
```
event: need.created | need.updated | need.resolved
event: claim.created | claim.lapsed | claim.delivered
event: announcement.published
event: zone.status
event: heartbeat            (every 25s — keeps proxies from killing the connection)
```
Fan-out across replicas via Redis pub/sub. Client must handle: reconnect with `Last-Event-ID`, and a full refetch if the gap is >2 min (don't try to replay a long history).

---

## 6. Frontend design

### 6.1 Routes
```
/                 Board (default: all zones, urgent first)
/zone/:code       Single-zone view
/post             Post a need (3-step wizard)
/claim/:needId    Claim flow → handoff code
/my               My claims, my shifts, my needs, "Forget this device"
/shifts           Roster grid
/aid              First aid points + emergency banner
/announce         Announcements feed
/give             For bulk/org contributors
/about            Privacy, transparency, what this is and isn't
/lite             Zero-JS fallback (server-rendered)
```

### 6.2 Offline strategy
- **Service worker:** app shell precached; `/zones`, `/needs`, `/announcements` use stale-while-revalidate; a visible "showing data from 4 min ago" chip whenever serving cache.
- **Write queue:** IndexedDB store `outbox` with `{idempotencyKey, method, path, body, attempts, createdAt}`. Background Sync API where available, plus a foreground drain on `online` event and on app focus.
- **Queue expiry:** an outbox item older than 6h is dropped with a user-visible notice — a stale need posted 8 hours late is worse than none.
- **Conflict on drain:** if a queued claim returns `409`, don't retry — surface it as "Zone D water got covered while you were offline; here are 3 other needs."

### 6.3 Battery/data discipline
- SSE connection closed when the tab is hidden >60s; reconnect on visible.
- Poll fallback at 60s if SSE fails twice.
- No animations above 60fps budget; no background timers when hidden.
- Dark mode default at night (OLED battery).

### 6.4 Key UI safeguards
- Claim confirmation step naming the zone and quantity (F2.E1 friction).
- Duplicate warning inline before posting (F1.E1).
- Non-dismissable emergency banner on `/aid`.
- Never render a raw error; every failure state has a suggested next action.
- Handoff code shown large, high contrast, and copyable — people will read it aloud in a noisy crowd.

---

## 7. Security checklist (implement all)

- [ ] `Content-Security-Policy: default-src 'self'; img-src 'self' data:; script-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'`
- [ ] `Permissions-Policy: geolocation=(), camera=(), microphone=(), payment=(), usb=()`
- [ ] `Referrer-Policy: no-referrer`
- [ ] `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload`
- [ ] `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`
- [ ] CORS: same-origin only; no wildcard
- [ ] JWT: ES256, ≤10 min TTL, `jti` checked against a Redis deny-list on every request
- [ ] TOTP for Steward/Admin; no SMS, no email recovery, no password reset by link
- [ ] Argon2id for the steward passphrase
- [ ] All SQL via parameterised queries / JOOQ / Spring Data — zero string concatenation
- [ ] Output encoding on all user text (React handles it; the `/lite` SSR path must escape explicitly — **this is the most likely XSS hole in the project**)
- [ ] `note_enc` encrypted app-side; DB credentials scoped to least privilege; no superuser at runtime
- [ ] Access logs disabled at nginx/Cloudflare, or IP truncated to /24
- [ ] Dependency scanning in CI; pin versions; no unmaintained transitive deps
- [ ] Secrets in a manager (not env files in the repo, not the DB)
- [ ] Rate-limit the `/lite` and `/board.pdf` endpoints too — PDF generation is a cheap DoS vector
- [ ] `/stats` returns only aggregates with a minimum bucket size of 5 (no k-anonymity leaks)
- [ ] Pen-test the moderation and admin endpoints specifically; they have the highest blast radius

---

## 8. Testing plan

### 8.1 Required test cases (map 1:1 to PRD edge case IDs)
Write a test named after each ID. Cursor: **generate a failing test for every `Fn.En` in the PRD first, then implement.**

Concurrency tests that must exist:
- `F2.E5_twoSimultaneousClaimsForLastUnit` — 50 parallel threads, assert exactly the right total pledged, no over-commit beyond 150%.
- `F1.E9_doubleSubmitSameIdempotencyKey` — assert one row, identical response both times.
- `F1.E2_fourthNeedInSameClusterRejected`.
- `F2.E3_deliveryConfirmedByThirdDevice`.
- `F1.E5_threeCoveredFlagsAutoResolve` — and the negative: same device flagging thrice does *not* resolve.

Property tests:
- `pledged` never exceeds `quantity * 1.5`, under any interleaving.
- `delivered` never exceeds `pledged`.
- No terminal need survives its retention window (run the purge job in the test).

Filter tests:
- Phone numbers in every Indian format, with spaces, dashes, `+91`, Devanagari digits → all stripped.
- Homoglyph and zero-width-char evasion → still caught.
- Medical keywords in Hindi and Hinglish → intercept fires.

Load test: 500 concurrent SSE clients, 50 writes/sec, on the target instance size.
Chaos: kill Redis mid-write (writes must fail closed with a clear message, never silently succeed); kill Postgres (app must serve cached reads and queue writes).

### 8.2 Manual pre-launch checklist
- [ ] Seize test: dump the DB and confirm you cannot identify a single human from it.
- [ ] Blackout test: turn off the network and confirm the printable board + `?lite=1` + cached read all work.
- [ ] Sunlight test: use it outdoors at noon.
- [ ] Low-battery test: 5% battery, 2G, 5-year-old Android.
- [ ] Purge test: run the end-of-event purge and verify with a fresh dump.

---

## 9. Deployment & ops

```
Environments: local (docker-compose) → staging → prod
Prod: 2× API containers (512MB each), managed Postgres (2 vCPU), managed Redis (256MB)
Estimated cost: well under $60/month at expected load.
```

- **Degraded modes:** `NORMAL → READ_ONLY → STATIC`. Test all three before launch, and document who can flip them and how, on paper, offline.
- **Runbook must exist** for: origin down, Redis down, DB down, DDoS, moderation flood, legal request, and end-of-event shutdown.
- **Backups:** encrypted, 24h retention, restore tested once. Anything longer contradicts §8.3 of the PRD.
- **On-call:** at least two people. A tool the site depends on with a single maintainer asleep at 3am is a liability.

---

## 10. Build order for Cursor

Do these in order. Do not start a step before the previous one's tests pass.

1. **Scaffold** — Spring Boot + Postgres + Redis via docker-compose; Vite React PWA shell; CI with the security headers test.
2. **Identity + PoW + rate limiting.** Build the abuse layer *before* the features. If you build features first you will ship without it.
3. **Zones** (seed data + SVG diagram component).
4. **Needs (F1)** with every edge case + the dedup trigger + content filter.
5. **Claims (F2)** with the race-safe update, handoff codes, lapse job.
6. **SSE + offline outbox.** Test the reconnect and conflict paths hard.
7. **`/lite` + printable board.** Do this early, not last — it's the blackout insurance.
8. **Shifts, announcements, aid points, stewards, moderation queue.**
9. **Bulk pledges, forecasting, heat band, static mirror, transparency page.**
10. **Purge verifier + end-of-event destruction flow.** Ship with v1.

### Cursor rules file (`.cursorrules`) — paste this in
```
This project coordinates humanitarian supply at a protest site. Privacy and abuse
resistance are hard requirements, not features.

NEVER:
- call navigator.geolocation or store any coordinate
- add fields for name, phone, email, age, gender, health info, or photos of people
- add direct messaging between users
- add person-finding or "missing person" features
- add third-party analytics, fonts, maps, or auth providers
- log user content or full IP addresses
- write raw SQL string concatenation
- ship a mutating endpoint without idempotency key + PoW + rate limit
- render user text in the /lite SSR path without explicit HTML escaping

ALWAYS:
- reference the PRD edge case ID (e.g. // F2.E5) in code that implements one
- write the failing test before the implementation
- return an actionable alternative with every 4xx, never a bare error
- set a TTL on every row you create
- keep the JS bundle under 100KB; justify any new dependency in the PR description
- support Hindi and English for every user-visible string
```

---

## 11. Before you write a line of code

Three things matter more than this document:

1. **Talk to the actual on-site coordinators.** If they're happily running on WhatsApp, a website they have to be persuaded to open will lose. A WhatsApp bot or a simple shared web link that *feeds* their existing group may be the higher-adoption design. Validate this first — it could save you the whole build.
2. **Get a lawyer to look at it.** Building infrastructure around a live political protest in India carries real legal exposure for you personally. Data protection, intermediary liability, and IT Act questions all apply. This document is engineering guidance, not legal advice.
3. **Decide who runs it after you.** Handover, domain ownership, and the shutdown plan should exist before launch, not after.
