-- Riding-style presets: named sets of routing preferences, per rider.
CREATE TABLE preset (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  rider_id         INTEGER NOT NULL REFERENCES rider (id) ON DELETE CASCADE,
  name             TEXT    NOT NULL,
  preferences_json TEXT    NOT NULL,
  -- Order in the preset list.
  position         INTEGER NOT NULL,
  created_at       TEXT    NOT NULL,
  updated_at       TEXT    NOT NULL,
  UNIQUE (rider_id, name)
);

-- Whether the rider got the built-in presets (DefaultPresets). Set once, so presets a rider
-- deletes do not come back. Existing riders get them on the next start.
ALTER TABLE rider ADD COLUMN presets_seeded INTEGER NOT NULL DEFAULT 0;
