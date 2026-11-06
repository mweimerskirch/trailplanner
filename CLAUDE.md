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
  to the rider (`RouteRepository` methods take `userId`); cloud connections are per rider too.
- Riding-style presets are per rider. The built-in ones are defined only in
  `presets.DefaultPresets`; riders get them once (`rider.presets_seeded`).
- BRouter rejects profiles that use tag values missing from its `lookups.dat`. Check
  `MTB_SCALE_VALUES` in `BRouterProfileGenerator` when upgrading BRouter.

## Frontend structure

Vue components follow atomic design, one folder per level in `frontend/src/components/`:
`atoms/` (AppLogo, Avatar, Badge, IconButton, StarToggle), `molecules/` (TabBar, StatusMessage,
StatTile, WarningList, RiderMenu, BreakdownBar, PlaceSearch, SortableHeader, ConfirmDialog), `organisms/` (AppHeader, MapView,
ElevationChart, RouteStats, ExportPanel, ImportDialog, PreferencesPanel) and
`templates/` (PlannerLayout, CenteredLayout, PageLayout). Routed pages live in `frontend/src/pages/`.
A component may only use components from its own level or below. Ask for confirmation with
`askConfirm()` from `confirm.ts` (shown by the one ConfirmDialog in App.vue), not `confirm()`. Shared state is in `planner.ts`,
`session.ts` and `presets.ts`, not in components.

## Plugin points

- Devices: implement `devices.DeviceIntegration` as a Spring bean (`@Order` sets its position).
  It declares export formats, transfer instructions, import instructions and an optional
  `CloudSync`. The UI builds the export panel from `GET /api/devices`, so a new device needs no
  frontend change. Cloud links are stored generically in `route_cloud_link` (see
  `CloudLinkRepository`); a cloud integration needs no columns of its own.
- Export formats: implement `export.RouteFormat` as a Spring bean. It is served at
  `/api/routes/{id}/export/{format}` and `/api/export/{format}`.
- Database: Flyway applies the SQL files in `backend/src/main/resources/db/migration` on startup.
  For a schema change add a new `V<next>__description.sql`; never edit a released file (Flyway
  checks checksums and refuses to start). Store timestamps with `db.Timestamps`.

