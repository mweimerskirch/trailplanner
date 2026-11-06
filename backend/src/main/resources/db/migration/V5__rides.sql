-- Recorded rides (activities), imported from FIT files: from a device over USB or uploaded.
CREATE TABLE ride (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  rider_id       INTEGER NOT NULL REFERENCES rider (id) ON DELETE CASCADE,
  name           TEXT    NOT NULL,
  -- e.g. 'cycling', 'running'; NULL if the file does not say.
  sport          TEXT,
  started_at     TEXT    NOT NULL,
  -- The device's local time minus UTC in seconds, to show the local start time; NULL if unknown.
  utc_offset_s   INTEGER,
  elapsed_s      REAL    NOT NULL,
  moving_s       REAL    NOT NULL,
  distance_m     REAL    NOT NULL,
  ascent_m       REAL    NOT NULL,
  descent_m      REAL    NOT NULL,
  -- In m/s; the heart rate, power and calories only if the device recorded them.
  max_speed      REAL,
  avg_heart_rate INTEGER,
  max_heart_rate INTEGER,
  avg_power      INTEGER,
  calories       INTEGER,
  -- The FIT file as imported; the track is read from it. NULL once the ride is deleted.
  fit            BLOB,
  -- Where the file came from (its name on the device or as uploaded) and its size: a device's
  -- files are only downloaded if no ride has that name and size.
  file_name      TEXT    NOT NULL,
  file_size      INTEGER NOT NULL,
  file_sha256    TEXT    NOT NULL,
  imported_at    TEXT    NOT NULL,
  -- Deleted rides keep their row, so a device import does not bring them back.
  deleted_at     TEXT,
  UNIQUE (rider_id, file_sha256)
);

CREATE INDEX ride_list ON ride (rider_id, deleted_at, started_at);
