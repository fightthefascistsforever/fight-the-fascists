-- Multi-chapter support: each protest site is an independent chapter

CREATE TYPE chapter_status AS ENUM ('PLANNED','ACTIVE','ARCHIVED');

CREATE TABLE chapters (
  id                SMALLSERIAL PRIMARY KEY,
  slug              TEXT NOT NULL UNIQUE,
  name_en           TEXT NOT NULL,
  name_hi           TEXT,
  location_label_en TEXT NOT NULL,
  location_label_hi TEXT,
  site_lat          DOUBLE PRECISION NOT NULL,
  site_lon          DOUBLE PRECISION NOT NULL,
  timezone          TEXT NOT NULL DEFAULT 'UTC',
  headcount_estimate INT NOT NULL DEFAULT 500,
  status            chapter_status NOT NULL DEFAULT 'PLANNED',
  public_url        TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  activated_at      TIMESTAMPTZ
);

INSERT INTO chapters (slug, name_en, name_hi, location_label_en, location_label_hi,
                      site_lat, site_lon, timezone, headcount_estimate, status, activated_at)
VALUES ('delhi-2026', 'Delhi 2026', 'दिल्ली २०२६',
        'Jantar Mantar, Delhi', 'जंतर मंतर, दिल्ली',
        28.627, 77.216, 'Asia/Kolkata', 800, 'ACTIVE', now());

-- Add chapter_id to chapter-scoped tables
ALTER TABLE zones ADD COLUMN chapter_id SMALLINT REFERENCES chapters(id);
UPDATE zones SET chapter_id = (SELECT id FROM chapters WHERE slug = 'delhi-2026');
ALTER TABLE zones ALTER COLUMN chapter_id SET NOT NULL;
ALTER TABLE zones DROP CONSTRAINT IF EXISTS zones_code_key;
CREATE UNIQUE INDEX zones_chapter_code_idx ON zones (chapter_id, code);

ALTER TABLE needs ADD COLUMN chapter_id SMALLINT REFERENCES chapters(id);
UPDATE needs n SET chapter_id = z.chapter_id FROM zones z WHERE n.zone_id = z.id;
ALTER TABLE needs ALTER COLUMN chapter_id SET NOT NULL;
CREATE INDEX needs_chapter_idx ON needs (chapter_id);

ALTER TABLE shifts ADD COLUMN chapter_id SMALLINT REFERENCES chapters(id);
UPDATE shifts s SET chapter_id = z.chapter_id FROM zones z WHERE s.zone_id = z.id;
ALTER TABLE shifts ALTER COLUMN chapter_id SET NOT NULL;

ALTER TABLE announcements ADD COLUMN chapter_id SMALLINT REFERENCES chapters(id);
UPDATE announcements SET chapter_id = (SELECT id FROM chapters WHERE slug = 'delhi-2026');
ALTER TABLE announcements ALTER COLUMN chapter_id SET NOT NULL;

ALTER TABLE bulk_pledges ADD COLUMN chapter_id SMALLINT REFERENCES chapters(id);
UPDATE bulk_pledges SET chapter_id = (SELECT id FROM chapters WHERE slug = 'delhi-2026');
ALTER TABLE bulk_pledges ALTER COLUMN chapter_id SET NOT NULL;

-- Update dedup trigger to scope by chapter via zone (zone_id already implies chapter)
-- No trigger change needed.

-- Drop global site_config (per-chapter config now in chapters table)
DROP TABLE IF EXISTS site_config;
