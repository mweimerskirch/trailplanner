PRAGMA journal_mode = WAL;

CREATE TABLE IF NOT EXISTS route (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  name             TEXT    NOT NULL,
  waypoints_json   TEXT    NOT NULL,
  preferences_json TEXT    NOT NULL,
  track_json       TEXT    NOT NULL,
  distance_m       REAL    NOT NULL,
  ascent_m         REAL    NOT NULL,
  descent_m        REAL    NOT NULL DEFAULT 0,
  created_at       TEXT    NOT NULL,
  updated_at       TEXT    NOT NULL,
  wahoo_route_id   INTEGER,
  wahoo_synced_at  TEXT
);

CREATE INDEX IF NOT EXISTS route_updated_at ON route (updated_at);

CREATE TABLE IF NOT EXISTS wahoo_token (
  id            INTEGER PRIMARY KEY CHECK (id = 1),
  access_token  TEXT NOT NULL,
  refresh_token TEXT NOT NULL,
  expires_at    TEXT NOT NULL
);
