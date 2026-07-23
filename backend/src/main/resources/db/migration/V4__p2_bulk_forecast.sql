-- P2: bulk pledges, site config for forecasting

CREATE TABLE bulk_pledges (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_name      TEXT NOT NULL,
  contact_note  TEXT,
  category      need_category NOT NULL,
  quantity      NUMERIC(10,2) NOT NULL CHECK (quantity > 0),
  unit          qty_unit NOT NULL,
  slot_hour     SMALLINT NOT NULL CHECK (slot_hour BETWEEN 0 AND 23),
  slot_label    TEXT NOT NULL,
  approved_by   BYTEA REFERENCES devices(device_hash),
  active        BOOLEAN NOT NULL DEFAULT true,
  missed_streak SMALLINT NOT NULL DEFAULT 0,
  prep_window_minutes SMALLINT,
  food_safety_ack BOOLEAN NOT NULL DEFAULT false,
  last_confirmed_at TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE site_config (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

INSERT INTO site_config (key, value) VALUES
  ('headcount_estimate', '800'),
  ('site_lat', '28.627'),
  ('site_lon', '77.216'),
  ('mirror_path', 'mirror');
