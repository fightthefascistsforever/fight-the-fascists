-- Fight the Fascists — initial schema (see ARCHITECTURE.md §3)

CREATE TYPE need_state    AS ENUM ('OPEN','CLAIMED','FULFILLED','EXPIRED','CANCELLED','WITHDRAWN');
CREATE TYPE need_category AS ENUM ('WATER','FOOD_COOKED','FOOD_DRY','ORS_ELECTROLYTE','MEDICAL_SUPPLY',
                                   'SHADE_TARPAULIN','BEDDING','SANITATION','POWER_CHARGING','CLOTHING','OTHER');
CREATE TYPE qty_unit      AS ENUM ('LITRES','MEALS','PACKETS','PIECES','PEOPLE_SERVED');
CREATE TYPE urgency_level AS ENUM ('ROUTINE','SOON','URGENT');
CREATE TYPE claim_state   AS ENUM ('ACTIVE','DELIVERED','LAPSED','CANCELLED');
CREATE TYPE trust_tier    AS ENUM ('ANON','TRUSTED','ZONE_STEWARD','ADMIN');
CREATE TYPE zone_status   AS ENUM ('ACTIVE','INACCESSIBLE','ARCHIVED');

CREATE TABLE zones (
  id              SMALLSERIAL PRIMARY KEY,
  code            TEXT NOT NULL UNIQUE,
  name_en         TEXT NOT NULL,
  name_hi         TEXT NOT NULL,
  landmark_en     TEXT NOT NULL,
  landmark_hi     TEXT NOT NULL,
  handoff_point   TEXT NOT NULL,
  svg_x           SMALLINT NOT NULL,
  svg_y           SMALLINT NOT NULL,
  status          zone_status NOT NULL DEFAULT 'ACTIVE',
  fallback_zone_id SMALLINT REFERENCES zones(id),
  sort_order      SMALLINT NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE devices (
  device_hash       BYTEA PRIMARY KEY,
  handle            TEXT NOT NULL,
  tier              trust_tier NOT NULL DEFAULT 'ANON',
  steward_zone_id   SMALLINT REFERENCES zones(id),
  reliability_score SMALLINT NOT NULL DEFAULT 50 CHECK (reliability_score BETWEEN 0 AND 100),
  deliveries_ok     SMALLINT NOT NULL DEFAULT 0,
  deliveries_lapsed SMALLINT NOT NULL DEFAULT 0,
  first_seen_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked           BOOLEAN NOT NULL DEFAULT false,
  pepper_version    SMALLINT NOT NULL DEFAULT 1
);
CREATE INDEX devices_last_seen_idx ON devices (last_seen_at);

CREATE TABLE needs (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  zone_id          SMALLINT NOT NULL REFERENCES zones(id),
  category         need_category NOT NULL,
  quantity         NUMERIC(10,2) NOT NULL CHECK (quantity > 0),
  unit             qty_unit NOT NULL,
  pledged          NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (pledged >= 0),
  delivered        NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (delivered >= 0),
  urgency          urgency_level NOT NULL,
  note_enc         BYTEA,
  state            need_state NOT NULL DEFAULT 'OPEN',
  created_by       BYTEA NOT NULL REFERENCES devices(device_hash) ON DELETE CASCADE,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  needed_by        TIMESTAMPTZ NOT NULL,
  expires_at       TIMESTAMPTZ NOT NULL,
  resolved_at      TIMESTAMPTZ,
  resolution_reason TEXT,
  covered_flags    SMALLINT NOT NULL DEFAULT 0,
  hidden_pending_review BOOLEAN NOT NULL DEFAULT false,
  version          INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX needs_open_idx ON needs (zone_id, needed_by)
  WHERE state IN ('OPEN','CLAIMED') AND hidden_pending_review = false;
CREATE INDEX needs_expiry_idx ON needs (expires_at) WHERE state IN ('OPEN','CLAIMED');

CREATE TABLE claims (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  need_id       UUID NOT NULL REFERENCES needs(id) ON DELETE CASCADE,
  device_hash   BYTEA NOT NULL REFERENCES devices(device_hash) ON DELETE CASCADE,
  quantity      NUMERIC(10,2) NOT NULL CHECK (quantity > 0),
  delivered_qty NUMERIC(10,2),
  eta           TIMESTAMPTZ NOT NULL,
  lapse_at      TIMESTAMPTZ NOT NULL,
  handoff_code  TEXT NOT NULL,
  state         claim_state NOT NULL DEFAULT 'ACTIVE',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at   TIMESTAMPTZ
);
CREATE INDEX claims_need_active_idx ON claims (need_id) WHERE state = 'ACTIVE';
CREATE INDEX claims_lapse_idx ON claims (lapse_at) WHERE state = 'ACTIVE';
CREATE UNIQUE INDEX claims_active_code_idx ON claims (handoff_code) WHERE state = 'ACTIVE';
CREATE INDEX claims_device_active_idx ON claims (device_hash) WHERE state = 'ACTIVE';

CREATE TABLE flags (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  target_type  TEXT NOT NULL,
  target_id    UUID NOT NULL,
  device_hash  BYTEA NOT NULL,
  reason       TEXT NOT NULL CHECK (reason IN ('ALREADY_COVERED','NOT_REAL','DUPLICATE','ABUSIVE')),
  weight       NUMERIC(3,1) NOT NULL DEFAULT 1.0,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX flags_device_target_idx ON flags (target_type, target_id, device_hash);

CREATE TABLE announcements (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  body_en       TEXT NOT NULL,
  body_hi       TEXT,
  source        TEXT NOT NULL CHECK (source IN ('OBSERVED_ON_SITE','ORGANISER_CONFIRMED','OFFICIAL_NOTICE')),
  urgent        BOOLEAN NOT NULL DEFAULT false,
  confirmations SMALLINT NOT NULL DEFAULT 1,
  published     BOOLEAN NOT NULL DEFAULT false,
  author_hash   BYTEA NOT NULL REFERENCES devices(device_hash),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  editable_until TIMESTAMPTZ NOT NULL,
  expires_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE aid_points (
  id          SMALLSERIAL PRIMARY KEY,
  zone_id     SMALLINT NOT NULL REFERENCES zones(id),
  name        TEXT NOT NULL,
  status      TEXT NOT NULL DEFAULT 'UNKNOWN',
  status_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  hours_note  TEXT,
  cannot_handle TEXT
);

-- F1.E2: dedup cluster cap trigger
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
