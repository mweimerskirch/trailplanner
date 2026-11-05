-- Favorite (starred) routes: listed first in the planner and filterable in the library.
ALTER TABLE route ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0;
