-- The DefaultPresets name of a built-in preset the rider has not renamed. Such a preset is shown in
-- the language of the UI; NULL for the rider's own presets and renamed built-in ones. Existing rows
-- are marked on startup (PresetService), by their name in any of the app's languages.
ALTER TABLE preset ADD COLUMN builtin TEXT;
