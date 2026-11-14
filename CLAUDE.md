# Trailplan

Self-hosted MTB route planner: Vue/MapLibre frontend (`frontend/`), Spring Boot backend with SQLite
(`backend/`), BRouter routing server (`brouter/`). See `README.md` for architecture and setup.

## Working rules

- Commit after every change, with a descriptive message. Do not push unless asked.
- Never delete or overwrite `data/`. It holds the user's downloaded map tiles and the route database
  (`data/db/trailplan.db`), which are not in git. Test against a copy, or stop and ask first.

## Checks

- Backend: Java 25 and Maven 3.10 from `.sdkmanrc`. Run `sdk env` in the project root (not in
  `backend/`), then `mvn verify` in `backend/`. It runs Error Prone, the tests and the Spotless
  format check. Fix formatting with `mvn spotless:apply`.
- Frontend: run `npm test`, `npm run typecheck`, `npm run lint` and `npm run build` in
  `frontend/`. Format with `npm run format`.
- Docker: `docker compose build`. The backend and BRouter containers must both run as uid 1000
  because they share `data/customprofiles`.

## Things to know

- Spring Boot 4 uses Jackson 3 (`tools.jackson.*`). The annotations stay in
  `com.fasterxml.jackson.annotation`. Jackson 3 rejects a missing value for a primitive field, so
  optional request fields must be boxed and get their default in the record constructor (see
  `RoutingPreferences`).
- `frontend/src/types.ts` mirrors the Java records. Keep the two in sync.
- Errors shown to the rider are `web.ApiException`s (status, title, optional `reason` for the UI);
  any other exception is an internal error (500).
- Riders: there are no passwords. The chosen rider is a cookie, resolved into a `CurrentUser`
  controller parameter (401 with reason `NO_USER` if missing). Every route query must be scoped
  to the rider (`RouteRepository` methods take `userId`).
- Riding-style presets are per rider. The built-in ones are defined only in
  `presets.DefaultPresets`; riders get each one once. `rider.presets_seeded` holds the
  `DefaultPresets.VERSION` a rider has; a new built-in preset gets the next version as `since`.
- Preferences have an `activity` (MTB, ROAD, HORSE; missing means MTB). Mountain-bike profiles must
  stay unchanged when adding rules for the other activities (`BRouterProfileGeneratorTest`).
- Translations (EN, FR, DE): the frontend uses vue-i18n; texts are in
  `frontend/src/i18n/messages/<area>.ts`, with `fr` and `de` typed as `en`. Never hard-code
  visible text; use `t()` (in .ts modules the exported `t`, called at render time, not at module
  load). `i18n.test.ts` checks keys, placeholders and plurals and that every `t('…')` key exists.
  Numbers go through `num`/`km` in `format.ts`. vue-i18n syntax: `|` separates plural forms and
  `@`, `{`, `}` are special in messages.
- The UI sends its language as Accept-Language. Server texts (device instructions, format labels,
  built-in preset names) are translated in `messages_fr/de.properties`, falling
  back to the English in the code. Route warnings carry a `code` the UI translates.
  A built-in preset keeps its DefaultPresets name in `preset.builtin` and is shown in the UI
  language until the rider renames it.
- BRouter rejects profiles that use tag values missing from its `lookups.dat`, including aliases
  (e.g. `horse=permissive` is folded into `yes`). Check `MTB_SCALE_VALUES` in
  `BRouterProfileGenerator` when upgrading BRouter, and route a test leg with each activity.

## Frontend structure

Vue components follow atomic design, one folder per level in `frontend/src/components/`:
`atoms/` (AppLogo, Avatar, Badge, IconButton, StarToggle), `molecules/` (TabBar, StatusMessage,
StatTile, WarningList, RiderMenu, BreakdownBar, PlaceSearch, SortableHeader, ConfirmDialog, TrailLegend,
LanguageSwitcher), `organisms/` (AppHeader, MapView,
ElevationChart, RouteStats, ExportPanel, ImportDialog, PreferencesPanel, DeviceSyncButton,
RideImportButton, TrackMap) and
`templates/` (PlannerLayout, CenteredLayout, PageLayout). Routed pages live in `frontend/src/pages/`.
A component may only use components from its own level or below. Ask for confirmation with
`askConfirm()` from `confirm.ts` (shown by the one ConfirmDialog in App.vue), not `confirm()`. Shared state is in `planner.ts`,
`session.ts` and `presets.ts`, not in components.

## Plugin points

- Devices: implement `devices.DeviceIntegration` as a Spring bean (`@Order` sets its position).
  It declares export formats, transfer instructions, import instructions and an optional
  `UsbSync` (label, device match, folder, format). The UI builds the export panel and the
  library's sync button from `GET /api/devices`, so a new device needs no frontend change.
- USB sync: `DeviceSyncService` mirrors the rider's active routes into the device folder and
  deletes other files only when the rider confirms them by name. Device access goes through
  `MtpDevice`; the real one (`LibmtpHelper`) runs the C helper `backend/mtp/trailplan-mtp.c`
  (libmtp), which is built in the backend Dockerfile. Tests use a fake `MtpDevice`. Device
  tasks run one at a time through `DeviceLock`.
- Rides: `rides.RideService` imports FIT activity files (uploaded, or from `UsbSync.ridesFolder`
  over USB). The FIT file is stored in the `ride` table and the track is read from it on demand
  (`RideFileReader`, on top of `imports.FitReader.decode`). Deleted rides keep their row so a device
  import skips them.
- Export formats: implement `export.RouteFormat` as a Spring bean. It is served at
  `/api/routes/{id}/export/{format}` and `/api/export/{format}`.
- Map layers: base maps and the raster overlay are in `map/layers.ts`; the trail overlay
  (`map/trails.ts`) styles the `transportation` layer of the OpenFreeMap vector source
  (OpenMapTiles schema: `class`, `subclass`, `mtb_scale`, `surface`, `access`, `bicycle`).
  `map/wayInfo.ts` shows the hovered way's details (names come from `transportation_name`).
- Database: Flyway applies the SQL files in `backend/src/main/resources/db/migration` on startup.
  For a schema change add a new `V<next>__description.sql`; never edit a released file (Flyway
  checks checksums and refuses to start). Store timestamps with `db.Timestamps`.

