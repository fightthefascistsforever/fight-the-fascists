# PRD — "Fight the Fascists": Protest Mutual-Aid Coordination Platform

> **Project name:** Fight the Fascists — protest mutual-aid coordination platform.
> **Form factor:** Responsive PWA (installable web app). No native app.
> **Audience for this doc:** an AI coding agent (Cursor) + a solo/small dev team.
> **Read this whole file before writing code. Then read ARCHITECTURE.md.**

---

## 0. TL;DR for the implementing agent

Build a mobile-first, anonymous-by-default web app that lets people **at a protest site** post *supply needs* (water, food, ORS, shade, first aid, phone charging, sanitation) pinned to **zones** — never to individuals — and lets people **outside** claim and fulfil those needs. Add volunteer shift rostering, verified announcements, and a first-aid locator.

The hard parts are **not** the CRUD. They are:
1. **Not building a surveillance tool.** The DB must be worthless if seized.
2. **Preventing duplicate/over-supply and ghost claims.**
3. **Stopping bad actors** without requiring identity.
4. **Working on 2G, throttled data, and full network blackouts.**

Every feature below has an **Edge Cases** block. Implement all of them. Do not skip any as "nice to have" — several are safety-critical.

---

## 1. Problem statement

A large, multi-week sit-in is running at a single fixed location (Jantar Mantar, Delhi). Hundreds to low thousands of people are present at varying times, in summer heat, some fasting, many camping overnight. Supply distribution today happens via WhatsApp groups and word of mouth, which produces:

- **Uneven distribution** — 400 water bottles arrive at the stage, while the far corner has none.
- **Duplication** — five donors all bring dinner at 8pm; nobody brings breakfast.
- **Invisible need** — the people worst off (heat exhaustion, no phone battery) are the least able to shout.
- **No handoff between shifts** — night volunteers don't know what day volunteers already did.
- **Rumour** — unverified "police are clearing the site" messages cause panic.
- **Donor friction** — people willing to help don't know *what* is needed, *where* to hand it over, or *whom* to contact.

## 2. Goals

| # | Goal | Success signal |
|---|---|---|
| G1 | Any on-site person can post a need in <20 seconds, without an account | Median time-to-post < 20s; >60% of needs posted by first-time devices |
| G2 | Any remote person can find a real, unclaimed, nearby need in <30 seconds | >70% of open needs get claimed within 45 min during daytime |
| G3 | Reduce duplicate supply | <10% of fulfilled needs flagged as "already covered" |
| G4 | Work at 50 kbps and offline | Full app shell < 150 KB gzipped; all read views available offline |
| G5 | Produce no data that endangers a user if the server is seized or subpoenaed | No PII at rest; see §8 |

## 3. Non-goals (explicitly out of scope — do not build)

- ❌ Any real-time tracking of individual people's locations.
- ❌ Direct 1:1 private messaging between users. (Huge moderation + safety liability, and it becomes a grooming/harassment vector. Coordination happens on public boards.)
- ❌ Payments, donations of money, or any financial rails. (Money invites FCRA/PMLA/tax scrutiny and fraud. Goods and time only.)
- ❌ Photo/video uploads of people. (Face data at a protest is the single most dangerous artifact you can create.)
- ❌ Organising, planning, or announcing anything unlawful. The app coordinates *humanitarian supply*, not tactics.
- ❌ Native iOS/Android apps. PWA only.
- ❌ Political content, slogans, campaigning, or petitions. Keeping the tool strictly logistical is both a moderation simplification and a legal shield.

---

## 4. Design principles (these override feature convenience)

1. **Data minimisation.** If you don't need the field, don't collect it. If you need it briefly, TTL it.
2. **Zones, not coordinates.** Needs are pinned to ~8–12 named, pre-defined zones of the site. No GPS capture, ever. No `navigator.geolocation` calls anywhere in the codebase.
3. **Pseudonymous by default.** A device gets a random handle ("Teal Ibex 41"). No phone, no email, no OAuth, no name.
4. **Ephemeral by default.** Needs auto-expire. Content is hard-deleted, not soft-deleted, past retention.
5. **Degrade, never fail.** Offline → cached read-only view + queued writes. Server down → static fallback page with the last-known board.
6. **Boring and small.** No heavy JS framework bloat, no fonts, no analytics SDKs, no third-party trackers, no ad tech, no Google Maps.
7. **Legible to a stressed, tired person.** Large tap targets, high contrast, works in sunlight, Hindi + English from day one.

---

## 5. Personas

| Persona | Context | Primary need |
|---|---|---|
| **Asha — on-site volunteer coordinator** | At site 12h/day, phone at 30%, manages a zone | Post needs fast, see what's incoming, avoid duplicates |
| **Ravi — remote contributor** | Lives 8km away, can bring 20L water this evening | See a real need, claim it, know exactly where/when to drop |
| **Sunita — bulk provider** | Runs a langar/restaurant, can do 200 meals daily | Commit to a recurring slot; not micromanage 40 small needs |
| **Imran — medic volunteer** | Doctor, comes 3 evenings/week | Publish first-aid point status; see heat-casualty patterns |
| **Priya — first-time drop-in** | Saw it on social media, wants to help today | Zero-friction path from link → useful action |
| **⚠️ Mallory — bad actor** | Wants to disrupt, doxx, spam, surveil, or discredit | (See §9 — design against her explicitly) |

---

## 6. Feature specifications

Each feature: **Description → User stories → Rules → Edge cases**. Edge cases are numbered `Fn.En` so you can reference them in tests.

---

### F1 — Need Board (core)

**Description.** The central artifact. A list/board of open supply needs, each attached to a Zone, with a category, quantity, urgency, and lifecycle state.

**States:** `OPEN → CLAIMED → FULFILLED` plus terminal `EXPIRED`, `CANCELLED`, `WITHDRAWN_BY_MOD`.

**Fields on a Need**
- `category`: enum — `WATER`, `FOOD_COOKED`, `FOOD_DRY`, `ORS_ELECTROLYTE`, `MEDICAL_SUPPLY`, `SHADE_TARPAULIN`, `BEDDING`, `SANITATION`, `POWER_CHARGING`, `CLOTHING`, `OTHER`
- `zone_id`: FK to a pre-seeded zone
- `quantity_value` + `quantity_unit` (enum: `LITRES`, `MEALS`, `PACKETS`, `PIECES`, `PEOPLE_SERVED`)
- `urgency`: `ROUTINE` (within 6h) | `SOON` (within 2h) | `URGENT` (within 30 min)
- `needed_by`: timestamp (derived default from urgency, editable)
- `note`: free text, max 200 chars
- `created_by_device_hash`, `created_at`, `expires_at`
- `state`, `claim_count`, `fulfilled_quantity`

**User stories**
- As Asha I can post "Zone D needs 40 litres water, URGENT" in three taps.
- As Ravi I can filter to `WATER` + `next 3 hours` and see what's genuinely unclaimed.
- As Ravi I can **partially claim** ("I'll bring 10 of the 40 L").

**Rules**
- A need is only fully covered when `sum(active claims) >= quantity`. Show a progress bar (`24 / 40 L pledged`).
- Once fully pledged, the need moves out of the default "Help needed now" view but remains visible under "Covered — awaiting delivery."
- Default TTL: `URGENT` 2h, `SOON` 6h, `ROUTINE` 12h. On expiry → `EXPIRED`, hidden from board, purged per §8.
- Only the creating device (or a Zone Steward, see F6) can edit/cancel.

**Edge cases — implement every one**
- **F1.E1 — Duplicate needs.** Before submitting, run a client-side check: same `zone_id` + `category` + open need existing → show "Zone D already has an open water need (24/40 L pledged). Add to it instead?" with a one-tap "Increase quantity by X" action. Never silently create a second one.
- **F1.E2 — Server-side dedup.** Even with E1, enforce at most **3 concurrent OPEN needs per (zone, category)**. 4th attempt returns `409 DUPLICATE_NEED_CLUSTER` with the existing need IDs so the client can merge.
- **F1.E3 — Over-fulfilment.** Claims may exceed quantity (people bring extra). Cap `pledged` display at 150% and then show "This is well covered — please check other zones." Redirect the claim flow to the next most-underserved need.
- **F1.E4 — Zero-quantity / absurd quantity.** Reject `quantity <= 0`. Cap per-need: water 2000 L, meals 5000, others 10000. Reject with `422 QUANTITY_OUT_OF_RANGE`. Absurd values are a spam signal (§9).
- **F1.E5 — Need created, then site situation changes** (e.g. a truck of water arrives). Any on-site device can hit **"Already covered"** on a need. 3 distinct devices marking it → auto-transition to `FULFILLED` with reason `COMMUNITY_RESOLVED`, and every claimant gets a push/SSE notice: "Zone D water is now covered — please redirect to Zone B."
- **F1.E6 — Creator's phone dies / they go home.** Needs must not be orphaned. Zone Stewards can cancel/edit any need in their zone. Plus auto-expiry handles the rest.
- **F1.E7 — Clock skew.** Client clocks are wrong. All timestamps are server-authoritative; client sends *durations*, not absolute times. Render relative times ("in ~2h") computed from a server-supplied `serverNow` in every response.
- **F1.E8 — Offline post.** Queue in IndexedDB with a client-generated `idempotency_key` (UUIDv4). On reconnect, POST with that key; server dedups. Show the queued item greyed with a "will send when online" badge.
- **F1.E9 — Double submit / flaky network retry.** Idempotency key required on all POST/PATCH. Server stores keys 24h in Redis, returns the original response on replay.
- **F1.E10 — Rapid-fire posting.** Rate limit: 5 needs per device per hour, 20 per zone per hour. Exceeding → soft block with a friendly message, not a hard ban (could be a genuinely busy coordinator — route to review queue instead).
- **F1.E11 — Need for something that shouldn't be crowdsourced.** Block a category keyword list at input (prescription drugs by name, anything weapon-adjacent, alcohol). Route to `MEDICAL_ESCALATION` flow (F5) instead of the open board. Never let the board become a channel for controlled substances.
- **F1.E12 — Text in the note field is abusive/political/contains a phone number.** Run the content filter (§9.4). Strip phone numbers and URLs from `note` automatically before persisting — this kills the doxxing and phishing vector in one move.
- **F1.E13 — Empty board.** Don't show a blank screen. Show "No open needs right now — the site is well supplied. Here's what's expected to run low next" (predictive, from F11) plus the volunteer shift board.
- **F1.E14 — Timezone.** Everything is IST. Store UTC, render `Asia/Kolkata`, hardcode, don't infer from browser.

---

### F2 — Claims / Pledges

**Description.** A remote contributor commits to bringing a quantity by a time.

**Fields:** `need_id`, `device_hash`, `quantity`, `eta` (server-derived from a duration the user picks: 30m/1h/2h/4h/tonight), `state` (`ACTIVE | DELIVERED | LAPSED | CANCELLED`), `handoff_code`.

**Rules**
- On claim, user gets a **handoff code** — a 4-character word-code (e.g. `MANGO`). At the drop point, they say the code; the receiving volunteer enters it to mark delivered. This is the only "identity" needed and it's single-use and disposable.
- Claim auto-lapses 60 min after ETA if not confirmed delivered. On lapse, quantity returns to the open pool and the need is re-broadcast.

**Edge cases**
- **F2.E1 — Ghost claims (the #1 failure mode).** Someone claims 40L to feel good and never shows. Mitigation: (a) short ETAs by default; (b) auto-lapse; (c) a **reliability score** on the device hash (see §9.5) that silently reduces how much a low-score device may claim at once; (d) at claim time, an explicit friction step: "You're pledging 40 L by 6:30pm. Zone D is relying on this. Confirm."
- **F2.E2 — Claimant arrives, nobody is there to receive.** Every zone must publish a `handoff_point` and a `steward_on_duty` window. If no steward is on duty, the claim UI shows the **central intake point** instead of the zone.
- **F2.E3 — Claimant delivers but forgets to confirm.** Any on-site device can mark `DELIVERED` using the handoff code. Also, need creator can mark received. Also, auto-lapse must not *punish* — lapsing is neutral; only a *pattern* of lapses affects reliability score.
- **F2.E4 — Partial delivery.** Allow `delivered_quantity < claimed_quantity`. Remainder returns to open pool immediately.
- **F2.E5 — Two people claim the last unit simultaneously.** Handle with optimistic concurrency: `UPDATE needs SET pledged = pledged + ? WHERE id = ? AND pledged + ? <= quantity * 1.5` and check row count. Loser gets `409 NEED_ALREADY_COVERED` + a suggested alternative need in the same response payload (never a dead end).
- **F2.E6 — Claimant cancels last-minute.** One-tap cancel. Instantly re-opens the quantity and fires an `URGENT` re-broadcast if `needed_by` is within 90 min. No shaming copy.
- **F2.E7 — Claim on an expired/cancelled need.** Return `410 NEED_NO_LONGER_OPEN` + 3 alternatives.
- **F2.E8 — Someone claims everything to starve the board.** (Denial-of-service via claiming.) Cap: max 3 active claims per device; max claim value per device scaled by reliability score; new devices capped at 1 active claim until first successful delivery.
- **F2.E9 — Handoff code collision.** 4-char codes from a curated 512-word list → collisions certain. Scope uniqueness to `(zone, active claims)` and regenerate on collision. Codes expire with the claim.

---

### F3 — Zones & the Site Map

**Description.** A pre-seeded, admin-defined list of 8–12 zones covering the site, each with a name, a plain-language landmark description, and a handoff point. Rendered as a **simple SVG floor-plan style diagram**, not a geographic map.

**Why an SVG diagram, not a real map:** it's ~5 KB instead of megabytes of tiles, works offline, is unambiguous to a tired person ("near the blue tarp by the north gate"), and it avoids embedding a third-party mapping SDK that phones home.

**Edge cases**
- **F3.E1 — Site layout changes** (police move barricades, the camp shifts). Zones are editable by admin, versioned. Needs reference `zone_id`, so renames propagate automatically. A `zone.status` of `INACCESSIBLE` hides it from new needs and shows a banner on existing ones.
- **F3.E2 — Zone deleted with open needs.** Forbid hard delete. `ARCHIVED` zones: existing needs are migrated to a designated `fallback_zone_id` with an audit note; the migration is announced via F7.
- **F3.E3 — Off-site needs.** Sometimes needs are at a hospital or a police station where people were taken. Add a `zone` of type `OFFSITE_REFERENCE` with only a name — never an address that identifies where a detained person is being held. Actually — safer: **do not** support off-site needs in v1. Route those to the legal/medical escalation contact (F5/F9) instead.

---

### F4 — Volunteer Shift Roster

**Description.** A grid of shifts (zone × time block × role) volunteers can claim.

**Roles:** `WATER_DISTRIBUTION`, `MEAL_SERVICE`, `SANITATION`, `FIRST_AID_SUPPORT`, `NIGHT_WATCH`, `INTAKE_DESK`, `ACCESSIBILITY_SUPPORT`, `TRANSLATION`.

**Rules**
- Shifts are 3-hour blocks, 24h grid, 7 days forward.
- Each shift has `min_volunteers` and `max_volunteers`. Under-staffed shifts surface at the top of the board and in the digest.
- A "**shift handover note**" (max 500 chars) that the outgoing volunteer writes and the incoming one sees. This single field solves most continuity loss.

**Edge cases**
- **F4.E1 — No-shows.** Shift shows `expected 4 / checked-in 1`. Under-staffed live shifts trigger an alert on the board.
- **F4.E2 — Overnight shift spanning midnight.** Store as UTC instants; never do date arithmetic on local date strings.
- **F4.E3 — Someone signs up for 12 consecutive hours.** Warn at >6h continuous: "Long shifts lead to burnout and heat exhaustion. Consider splitting." Allow, but flag to stewards.
- **F4.E4 — A minor signs up.** Do **not** collect age. Instead, show a standing notice on `NIGHT_WATCH` shifts: "Overnight roles are for adults only." Roles that involve overnight presence require a Zone Steward to confirm the sign-up. Do not build any flow that collects information about whether a user is a minor.
- **F4.E5 — Mass sign-up spam** (one actor filling every shift to make the roster look full while nobody shows). Cap 2 active future shift claims per device until one is completed. Stewards can release a shift claim.

---

### F5 — First Aid & Medical Points

**Description.** A read-mostly directory of on-site first-aid points: zone, operating hours, current status (`OPEN | CLOSED | AT_CAPACITY`), and what they *cannot* handle (so people know when to call an ambulance).

**Critical rules**
- Prominent, always-visible: **"Medical emergency → call 102 / 108 immediately. Do not use this app."** This banner is non-dismissable on the medical screen.
- The app **never** provides medical advice, triage guidance, or treatment instructions. It only says where the first-aid point is and when to call an ambulance.
- **No health information about any individual is ever entered, stored, or displayed.** Not names, not conditions, not "person in Zone C has collapsed." A collapse is a phone call, not a form submission.
- Heat-illness risk banner driven by a simple temperature threshold (see F12) — general public-health messaging only (hydrate, shade, watch for dizziness), the same text a government advisory would carry.

**Edge cases**
- **F5.E1 — Someone tries to use the need board for a medical emergency.** Keyword detection on need notes (`collapsed`, `unconscious`, `bleeding`, `बेहोश`, `चक्कर`…) → interrupt with a full-screen modal showing emergency numbers and the nearest first-aid point, before the post is submitted. Log nothing about the content.
- **F5.E2 — Stale status.** First-aid statuses expire after 4h and revert to `UNKNOWN — call ahead` rather than showing a confidently wrong "OPEN."
- **F5.E3 — Hunger strike context.** Given fasting participants, refeeding and medical supervision are genuinely dangerous areas. The app must carry a standing notice: medical supervision of a fast is a clinician's job. Absolutely no guidance content in-app. Link to nothing; name no protocols.

---

### F6 — Roles & Trust Tiers

| Tier | How obtained | Powers |
|---|---|---|
| `ANON` | Default, on first visit | Post needs, claim, mark delivered |
| `TRUSTED` | 3 successful deliveries, or vouched by 2 Stewards | Higher rate limits, can mark "already covered" with weight 2, claims not throttled |
| `ZONE_STEWARD` | Granted by Admin, scoped to one zone | Edit/cancel any need in zone, confirm shift sign-ups, set zone status, resolve flags |
| `ADMIN` | Bootstrapped via server-side config | Zones, announcements, moderation, kill switch |

**Rules**
- Steward/Admin auth uses a **passphrase + TOTP**, not SMS. No phone numbers anywhere.
- Steward grants are made offline (in person) and entered by an Admin, who generates a one-time claim link valid 30 minutes.
- **Any Steward or Admin can be revoked in one action, and revocation is instant** (short-lived JWTs, ≤10 min, plus a Redis deny-list checked per request).

**Edge cases**
- **F6.E1 — Steward device seized or compromised.** Admin revokes; all their sessions die within 10 min; their past actions are visible in a *privacy-scrubbed* audit log (action + timestamp, no content). Provide a documented, tested "revoke all stewards" panic action.
- **F6.E2 — Admin account compromised.** Admin actions that are destructive (purge, zone archive, mass-announce) require a second admin's approval (2-of-N) *if* more than one admin exists. Rate-limit admin writes. Alert on any admin login.
- **F6.E3 — Vouching rings.** A cluster of colluding devices vouching each other into `TRUSTED`. Mitigate: vouching only by Stewards, never peer-to-peer; `TRUSTED` via deliveries requires deliveries confirmed by *distinct* devices in *distinct* zones.

---

### F7 — Verified Announcements

**Description.** A one-way, Admin/Steward-only feed for logistics facts: "Water tanker arriving Gate 2 at 4pm," "Zone C moved," "Roster changes." Pinned, timestamped, signed by role.

**Hard rule:** announcements are **logistics only**. No political statements, no calls to action, no claims about police movements or legal advice, no unverified rumours. This keeps the tool defensible and prevents it becoming a rumour amplifier.

**Edge cases**
- **F7.E1 — Rumour laundering.** Users will ask stewards to post unverified things. Enforce a required `source` field on every announcement (`OBSERVED_ON_SITE | ORGANISER_CONFIRMED | OFFICIAL_NOTICE`) rendered prominently. Anything else can't be posted.
- **F7.E2 — Announcement is wrong.** Announcements are editable for 15 min, then only retractable. A retraction posts a visible `CORRECTION` item — never a silent delete.
- **F7.E3 — Notification fatigue.** Max 6 push-level announcements per day; the rest are feed-only. Hard cap enforced server-side.
- **F7.E4 — Mass panic message.** Any announcement flagged `URGENT` requires two distinct Steward/Admin confirmations within 5 minutes before it broadcasts. This is a deliberate speed bump on the highest-blast-radius action in the system.

---

### F8 — Bulk / Recurring Contributions

**Description.** A separate lane for organisations (langars, restaurants, NGOs, water suppliers) who can commit "200 meals daily at 8pm for a week" rather than picking off 40 small needs.

**Rules**
- Bulk pledges create a **recurring supply schedule** that the need-generation logic subtracts from projected demand (F11) — so the board doesn't ask for dinner when dinner is already handled. This is the single highest-leverage anti-duplication feature.
- Bulk contributors provide an **org name + one contact channel of their choosing** (they're publicly operating businesses/orgs, this is a different privacy calculus from individuals). Still optional; still deletable.

**Edge cases**
- **F8.E1 — Recurring pledge silently stops.** Auto-check: if a recurring slot isn't confirmed delivered 2 days running, downgrade it to `UNCONFIRMED` and re-open demand. Never let a phantom commitment starve the site.
- **F8.E2 — Food safety.** Cooked food in Delhi summer is a mass-casualty risk. Require an acknowledged checklist on cooked-food pledges: prepared within 4 hours, transported covered, no dairy-heavy dishes in the afternoon slot, labelled with prep time. Display prep time on the delivery. **Auto-reject cooked-food pledges with prep-to-delivery > 4 hours.**
- **F8.E3 — A "bulk contributor" that's a front for disruption** (deliberately spoiled food, or a no-show that starves the site). Bulk pledges above a threshold require Steward approval before they're counted against demand. Until approved, they're *additive bonus*, not *demand-reducing*.
- **F8.E4 — Over-commitment collapse.** If total bulk pledges exceed projected demand by >200%, stop accepting new bulk pledges for that slot and publicly show "Dinner is over-subscribed; breakfast needs help."

---

### F9 — Support Contacts Directory

**Description.** A static, admin-curated list of *organisational* contacts: legal aid helplines, ambulance numbers, women's helpline, a lost-and-found desk, the site's central intake desk.

**Rules**
- **Organisations and published helplines only.** Never an individual's personal number. Never a lawyer's personal mobile.
- No "report a detention" form, no logging of who contacted whom, no case tracking. That data is radioactive; the app must not hold it. The directory hands off to a phone number and forgets.

---

### F10 — Lost & Found / Reunification (⚠️ handle with extreme care)

**Description.** A minimal board for **objects only** — bag, phone, ID card (described, never photographed or transcribed).

**Explicit design decision: do NOT build person-finding.** A "looking for a missing person" feature at a protest creates a searchable database of who was present, which is precisely the artifact we refuse to create, and it is trivially abusable for stalking. Route person-reunification to the physical intake desk (F9). State this decision in the codebase comments so nobody "helpfully" adds it later.

**Edge cases**
- **F10.E1 — Someone posts a person anyway.** Content filter for person-descriptive patterns → block with an explanation and the intake desk location.
- **F10.E2 — ID card found.** Do not transcribe name/number. Post as "ID card found, Zone B — describe it at the intake desk to claim." Claiming is by challenge-response in person, never in the app.
- **F10.E3 — TTL.** Lost & found entries purge after 72h, unconditionally.

---

## 7. New feature ideas (beyond your original list)

Ranked by leverage-to-effort.

1. **⭐ Demand forecasting / "what runs out next" (F11).** Simple model: `projected_need(category, hour) = headcount_estimate × per_capita_rate(category, temperature_band)` minus confirmed incoming supply. Even a crude version turns the app from reactive to proactive and is the biggest real-world win. Post-hoc it also gives organisers a demand curve for the whole camp.

2. **⭐ Heat-index advisory band (F12).** Pull temperature from IMD/open-meteo; drive a site-wide banner (green/amber/red) and automatically bump water/ORS demand coefficients. In a Delhi July sit-in with fasting participants, heat is the dominant health risk.

3. **⭐ Physical fallback: printable board.** One-tap "print today's needs as an A4 poster with a QR code." Pinned at the intake desk, it makes the system work for people without smartphones and survives a network blackout. Underrated; do it.

4. **⭐ SMS/USSD-free low-bandwidth mode.** A `?lite=1` route serving a <10 KB server-rendered HTML page, no JS, form POSTs only. Works on a feature phone browser and on 2G. Also your fallback when the SPA fails.

5. **Shift handover digest.** Auto-generated summary at each shift boundary: what was delivered, what lapsed, what's inbound, what's under-staffed. Push to the outgoing/incoming stewards.

6. **"Adopt a zone" for remote contributors.** A remote group commits to keeping one zone stocked. Creates ownership and dramatically reduces the coordination surface.

7. **Accessibility & inclusion needs lane.** Wheelchair access, sanitary supplies, baby supplies, elder support, prayer space logistics. These needs are systematically under-reported because people are embarrassed to ask publicly — so allow posting them **without** a visible poster handle at all.

8. **Language toggle: Hindi / English / Ladakhi transliteration.** Ship Hindi at launch, not later. Store `locale` in localStorage, never on the server.

9. **Waste & sanitation tracker.** Bins full, toilets needing cleaning, water-logging. Boring, unglamorous, and the actual difference between a camp that lasts and one that gets shut down on health grounds.

10. **Post-event data destruction ceremony.** A documented, one-click, verifiable purge when the sit-in ends, with a public statement of what was destroyed. Build it on day one; it's a trust feature, not an afterthought.

11. **Transparency ledger.** Public aggregate stats — litres delivered, meals served, volunteer hours — with zero individual attribution. Great for legitimacy and press, and it costs nothing extra.

12. **Read-only public mirror.** A static, cacheable JSON+HTML snapshot regenerated every 60s on a separate cheap host, so if the main app is DDoSed or taken down, the board is still readable.

---

## 8. Privacy & data-protection specification (non-negotiable)

This section is a requirement, not advice. Implement literally.

### 8.1 What we never collect
Phone numbers · email addresses · real names · GPS coordinates · IP addresses in application logs · device fingerprints beyond a salted random ID · photos of people · biometrics · any health information about an individual · social login · anything about political affiliation or beliefs.

### 8.2 Device identity
- On first load, generate a UUIDv4 client-side → `device_secret`, stored in localStorage.
- Server stores only `device_hash = HMAC-SHA256(device_secret, server_pepper)`.
- `server_pepper` lives in the secret manager, **not** the DB. Rotating it invalidates all links between old and new data — this is a feature. Rotate it on a schedule (e.g. weekly) with a mapping table that itself expires in 7 days.
- Provide a visible **"Forget this device"** button: clears localStorage and issues `DELETE /me` which purges all rows for that hash.

### 8.3 Retention (enforce with a scheduled job AND a DB-level policy)
| Data | Retention |
|---|---|
| Needs, claims | 24h after terminal state → hard delete |
| Shift sign-ups | 24h after shift end → hard delete |
| Announcements | 7 days |
| Lost & found | 72h |
| Moderation flags | 48h |
| Web-server access logs | **Disabled.** If unavoidable, IPs truncated to /24 and rotated hourly. |
| Application logs | No user content, no identifiers. Structured events only. |
| Aggregate counters | Indefinite — but only counters, never rows |
| Backups | 24h rolling, encrypted, auto-expiring. **A backup that outlives the retention policy defeats it.** |

### 8.4 At rest and in transit
- TLS 1.3 only, HSTS, no mixed content.
- `note` fields encrypted at rest with an app-level key (so a stolen DB dump is not readable without the app's KMS access).
- No PII in URLs, ever (they end up in referrers and proxy logs).
- Strict CSP, `Referrer-Policy: no-referrer`, `Permissions-Policy` denying geolocation/camera/microphone at the HTTP header level so the browser itself blocks any accidental future call.

### 8.5 Legal posture
- Publish a short, plain-language privacy notice and a **transparency page** stating exactly what is stored and for how long.
- Have a documented, pre-decided answer for a data request from authorities: "here is what exists; most of it does not." Design so that answer is boring.
- India's DPDP Act 2023 applies. Data minimisation is both the ethical and the compliant path. **Get an actual Indian lawyer to review before launch — this document is not legal advice.**
- Consider hosting jurisdiction deliberately, and consider who owns the domain.

---

## 9. Anti-abuse: threat model & countermeasures

### 9.1 Threat actors
| Actor | Motive | Primary attack |
|---|---|---|
| Trolls | Chaos, lulz | Spam needs, fake claims, offensive text |
| Organised disruptors | Discredit / starve the protest | Ghost claims at scale, fake bulk pledges, poisoned-food pledge, flooding |
| Surveillance-minded actor | Map participants | Scrape the board, correlate handles, seed needs to see who responds |
| Doxxers / harassers | Target individuals | Post phone numbers, describe individuals, use notes as a message channel |
| Commercial spammers | Traffic | URLs and promo text in notes |
| Well-meaning chaos | Genuine, but duplicative | Over-supply, stale needs, rumour |

### 9.2 Layered defences

**Layer 1 — Structural (best defence: nothing valuable to steal)**
- No individual location data, no PII, aggressive TTLs. A scraper gets "Zone D wants water," which is not intelligence.
- Handles are per-device and rotate with the pepper, so long-term identity correlation across weeks is broken by design.

**Layer 2 — Proof-of-work / bot friction (no CAPTCHA vendor — those are third-party trackers)**
- Self-hosted **hashcash-style PoW challenge** on write endpoints. ~200ms for a real user, expensive at scale for a spammer. Difficulty scales up automatically under attack.
- Honeypot form fields.
- Timing check: form submitted <1.5s after render → challenge.

**Layer 3 — Rate limits (per device, per zone, per IP-prefix, global)**
- Token bucket in Redis. Limits per §F1.E10, §F2.E8, §F4.E5.
- Global circuit breaker: if writes exceed N/min site-wide, automatically raise PoW difficulty and switch new devices to review-queue mode. Never fully close writes — the site's real needs must still get through.

**Layer 4 — Content filtering (`note`, announcements, lost&found)**
- Deterministic strip: URLs, phone numbers (Indian + international patterns), email addresses, UPI IDs, long digit strings.
- Blocklist: slurs (Hindi + English + transliterated), weapon/drug/alcohol terms, doxxing patterns ("wearing a red shirt, tall, near…").
- Length caps and character-class limits (block excessive scripts/emoji floods used for rendering attacks).
- Anything caught → **soft fail**: the post goes to a review queue and the user sees "sent for a quick check," rather than an error that teaches the attacker your filter's boundaries.

**Layer 5 — Community signal**
- "Already covered," "Doesn't look real," "Duplicate" — three lightweight flags. **Weighted by trust tier and de-duplicated by device.**
- 3 weighted flags → auto-hide pending Steward review (5 min SLA target). This is the fast path; humans are the slow path.
- **Guard the flag itself:** brigading is the obvious counter-attack. Require flaggers to be distinct devices, cap flags per device per hour, and weight `ANON` flags at 0.5 vs `TRUSTED` at 2.

**Layer 6 — Reliability scoring (silent, non-punitive-looking)**
- Per-device score from delivery follow-through. Low score → lower claim caps and lower flag weight. **Never displayed publicly** (a visible reputation score becomes a target and a social hierarchy).
- Score decays toward neutral; nobody is permanently condemned by one bad day.
- Never ban outright on score alone; degrade capability instead. Bans are trivially evaded by clearing localStorage anyway — so the design goal is *make abuse low-yield*, not *make it impossible*.

**Layer 7 — Steward moderation**
- Queue of flagged/filtered items with one-tap approve/remove.
- Steward actions are logged as (action, target_id, timestamp, steward_id) — never content.

**Layer 8 — Operational**
- Cloudflare (or equivalent) in front for DDoS + bot scoring. Configure it to **not** log or store more than you do.
- Kill switch: one command puts the site in read-only mode with a banner. Another puts it fully static.
- Alerting on anomaly: sudden spike in a single category, in one zone, from a narrow IP range, or a burst of new devices.

### 9.3 Specific attacks and the specific counter
| Attack | Counter |
|---|---|
| Flood 500 fake needs | PoW + per-device/zone rate limits + dedup cluster cap (F1.E2) + global circuit breaker |
| Claim everything, deliver nothing | Active-claim caps, new-device cap of 1, auto-lapse, reliability score (F2.E1/E8) |
| Fake bulk pledge to starve dinner | Steward approval before a pledge reduces projected demand (F8.E3) |
| Poisoned / unsafe food | 4-hour prep rule auto-reject, prep-time labelling, Steward-approved bulk (F8.E2) |
| Post phone number to doxx | Automatic pattern strip before persistence (F1.E12) |
| Use notes as a covert message channel | 200-char cap, filtering, no threads, no replies, no DMs |
| Brigade the flag system to remove real needs | Weighted flags, distinct-device requirement, Steward review before permanent removal |
| Scrape the board to map the protest | Nothing personal on the board; aggressive TTL; rate-limited public API; no bulk export endpoint |
| Seed a fake "urgent" need to observe responders | Responders aren't identified to the poster; only a disposable handoff code is exchanged |
| Sybil devices | PoW cost, new-device capability floor, deliveries-must-be-confirmed-by-distinct-devices-in-distinct-zones |
| Compromise a Steward account | TOTP, ≤10-min tokens, instant revocation, scoped-to-one-zone powers |
| Take the site down | Static read-only mirror, printable board, `?lite=1` mode |

### 9.4 What we deliberately do NOT do
- No identity verification, ID upload, or phone OTP. It would work — and it would create exactly the database that must not exist.
- No shadow-banning without a path back. Degraded users always see *why* their post is under review.
- No behavioural profiling or "risk scoring" of people. Only reliability of *deliveries*, which decays and is invisible.

---

## 10. Accessibility & i18n
- WCAG 2.2 AA. Real focus states, 44×44px targets, semantic HTML, works with a screen reader.
- Full keyboard operation; works with browser zoom at 200%.
- High-contrast palette that survives direct sunlight; dark mode for night shifts (battery + eyes).
- Hindi and English at launch; strings in JSON, no hardcoded text; RTL-safe layout even though not needed yet.
- Everything must be usable one-handed on a 5" screen with a cracked digitiser.

## 11. Performance budget
- App shell ≤ 150 KB gzipped, JS ≤ 100 KB. No web fonts. SVG icons inlined.
- FCP < 1.5s on Slow 3G, TTI < 3s.
- Board list endpoint response ≤ 15 KB for 100 needs.
- `?lite=1` page ≤ 10 KB, zero JS.
- Every list is paginated; nothing unbounded.

## 12. Metrics (aggregate only, no per-user analytics)
Needs posted/fulfilled per hour · median time-to-claim · median time-to-delivery · lapse rate · duplicate rate · shift fill rate · % needs from `TRUSTED` vs `ANON` · flags raised/upheld · `?lite=1` usage share.
**Self-host the counters. No Google Analytics, no Segment, no Sentry with default PII capture.**

## 13. Phasing
- **P0 (build first, ~1 week):** Zones, Need Board (F1), Claims (F2), device identity, rate limits, PoW, content filter, `?lite=1`, Hindi/English. This alone is useful.
- **P1:** Shifts (F4), Announcements (F7), First Aid directory (F5), Stewards (F6), moderation queue.
- **P2:** Bulk pledges (F8), forecasting (F11), heat band (F12), printable board, static mirror, transparency page.
- **P3:** Accessibility lane, sanitation tracker, adopt-a-zone, shift digest.

## 14. Risks & open questions
1. **Adoption beats features.** If the on-site coordinators don't use it, it's dead. Talk to actual organisers *before* building P1. Consider that a WhatsApp-bot front-end might get 10× the adoption of a website — worth testing.
2. **Legitimacy.** An unofficial app claiming to coordinate a protest can itself become a disinformation vector or be *accused* of being one. Get explicit organiser endorsement, or publish clearly that it's independent and logistical only.
3. **Liability.** Food poisoning, a volunteer injured on a shift, a medical incident. Carry clear disclaimers, keep the tool strictly informational, and take legal advice.
4. **Network blackout.** Mobile internet suspensions have happened at related protests. The printable board and `?lite=1` are the answer; assume they'll be needed.
5. **You are building this for a live, high-stakes political situation.** Ship the privacy and abuse layers *with* v1, not after. A half-built version of this is worse than none.
