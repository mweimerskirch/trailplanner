-- The rider's device (a DeviceIntegration id, e.g. 'wahoo-elemnt'); NULL: files only (GPX, FIT).
-- The UI shows device buttons and instructions only for this device.
ALTER TABLE rider ADD COLUMN device TEXT;

-- Riders who already imported rides from an ELEMNT keep seeing its sync and import buttons.
UPDATE rider SET device = 'wahoo-elemnt'
WHERE id IN (SELECT rider_id FROM ride WHERE file_name LIKE '%ELEMNT%');
