-- Trailplan schema, version 1. Applied by Flyway on startup.
-- Never edit this file once released: add a new V<n>__description.sql instead.
-- Timestamps are ISO-8601 UTC text with millisecond precision (see app.trailplan.db.Timestamps).

-- A rider: a name without password. Everything else belongs to a rider.
CREATE TABLE rider (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  name       TEXT    NOT NULL,
  created_at TEXT    NOT NULL
);

CREATE TABLE route (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  rider_id         INTEGER NOT NULL REFERENCES rider (id) ON DELETE CASCADE,
  name             TEXT    NOT NULL,
  -- Where the route came from: PLANNER, GPX, FIT or KOMOOT.
  source           TEXT    NOT NULL,
  -- Identifies an imported file or tour (e.g. 'komoot:123'), so it is imported only once per rider.
  external_id      TEXT,
  -- JSON list of waypoints; NULL for an imported route that has not been edited yet.
  waypoints_json   TEXT,
  preferences_json TEXT    NOT NULL,
  -- The current track: calculated from the waypoints, or the imported track while not edited.
  track_json       TEXT    NOT NULL,
  distance_m       REAL    NOT NULL,
  ascent_m         REAL    NOT NULL,
  descent_m        REAL    NOT NULL,
  -- Imported routes: the original track and its statistics as imported. Edits never change it,
  -- so the original can always be restored or downloaded.
  original_json    TEXT,
  created_at       TEXT    NOT NULL,
  updated_at       TEXT    NOT NULL,
  archived_at      TEXT,
  deleted_at       TEXT,
  UNIQUE (rider_id, external_id)
);

CREATE INDEX route_list ON route (rider_id, deleted_at, archived_at, updated_at);

-- A rider's connection to a cloud service (e.g. Wahoo), with its OAuth tokens.
CREATE TABLE cloud_account (
  rider_id      INTEGER NOT NULL REFERENCES rider (id) ON DELETE CASCADE,
  service       TEXT    NOT NULL,
  access_token  TEXT    NOT NULL,
  refresh_token TEXT    NOT NULL,
  expires_at    TEXT    NOT NULL,
  PRIMARY KEY (rider_id, service)
);

-- Which remote route (per cloud service) belongs to which saved route.
CREATE TABLE route_cloud_link (
  route_id  INTEGER NOT NULL REFERENCES route (id) ON DELETE CASCADE,
  service   TEXT    NOT NULL,
  remote_id TEXT    NOT NULL,
  synced_at TEXT    NOT NULL,
  PRIMARY KEY (route_id, service)
);
