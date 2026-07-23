-- P1: shifts, moderation audit, steward credentials, announcement corrections

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
CREATE UNIQUE INDEX shifts_zone_role_start_idx ON shifts (zone_id, role, starts_at);

CREATE TABLE shift_signups (
  shift_id     UUID NOT NULL REFERENCES shifts(id) ON DELETE CASCADE,
  device_hash  BYTEA NOT NULL REFERENCES devices(device_hash) ON DELETE CASCADE,
  confirmed    BOOLEAN NOT NULL DEFAULT false,
  checked_in_at TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (shift_id, device_hash)
);

CREATE TABLE mod_audit (
  id          BIGSERIAL PRIMARY KEY,
  actor_hash  BYTEA NOT NULL,
  action      TEXT NOT NULL,
  target_id   UUID,
  at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE steward_credentials (
  device_hash       BYTEA PRIMARY KEY REFERENCES devices(device_hash) ON DELETE CASCADE,
  passphrase_hash   TEXT NOT NULL,
  totp_secret_enc   BYTEA NOT NULL,
  tier              trust_tier NOT NULL,
  steward_zone_id   SMALLINT REFERENCES zones(id),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked           BOOLEAN NOT NULL DEFAULT false
);

ALTER TABLE announcements ADD COLUMN IF NOT EXISTS retracted_by UUID REFERENCES announcements(id);
ALTER TABLE announcements ADD COLUMN IF NOT EXISTS correction_of UUID REFERENCES announcements(id);

-- Seed shifts: next 48 hours, 3-hour blocks, key roles per zone
INSERT INTO shifts (zone_id, role, starts_at, ends_at, min_volunteers, max_volunteers)
SELECT z.id, r.role,
       ts AS starts_at,
       ts + interval '3 hours' AS ends_at,
       2, 6
FROM zones z
CROSS JOIN (VALUES
  ('WATER_DISTRIBUTION'),
  ('MEAL_SERVICE'),
  ('INTAKE_DESK'),
  ('NIGHT_WATCH')
) AS r(role)
CROSS JOIN generate_series(
  date_trunc('hour', now()) + interval '1 hour',
  date_trunc('hour', now()) + interval '7 days',
  interval '3 hours'
) AS ts
WHERE z.status = 'ACTIVE'
  AND extract(hour from ts at time zone 'Asia/Kolkata') BETWEEN 6 AND 23
ON CONFLICT DO NOTHING;
